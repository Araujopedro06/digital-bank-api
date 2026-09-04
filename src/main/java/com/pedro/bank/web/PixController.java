package com.pedro.bank.web;

import com.pedro.bank.domain.PixKey;
import com.pedro.bank.service.AccountService;
import com.pedro.bank.service.PixKeyService;
import com.pedro.bank.service.PixService;
import com.pedro.bank.web.dto.BrCodeParseRequest;
import com.pedro.bank.web.dto.BrCodeParseResponse;
import com.pedro.bank.web.dto.BrCodeRequest;
import com.pedro.bank.web.dto.BrCodeResponse;
import com.pedro.bank.web.dto.PixChargeLookupResponse;
import com.pedro.bank.web.dto.PixChargeRequest;
import com.pedro.bank.web.dto.PixChargeResponse;
import com.pedro.bank.web.dto.PixKeyRequest;
import com.pedro.bank.web.dto.PixKeyResponse;
import com.pedro.bank.web.dto.PixLookupRequest;
import com.pedro.bank.web.dto.PixRecipientResponse;
import com.pedro.bank.web.dto.PixTransferRequest;
import com.pedro.bank.web.dto.TransactionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Pix: keys that stand in for an account number, and the codes that carry them.
 *
 * <p>Looking a key up is a POST with a body rather than a GET with a query
 * parameter. It reads oddly for a read, and it is deliberate: the key is somebody's
 * CPF, phone number or e-mail, and query strings end up in access logs, proxy logs
 * and browser history.
 */
@RestController
@RequestMapping("/api/pix")
@Tag(name = "Pix")
@SecurityRequirement(name = "bearerAuth")
public class PixController {

    private final PixKeyService pixKeyService;
    private final PixService pixService;
    private final AccountService accountService;

    public PixController(PixKeyService pixKeyService, PixService pixService,
                         AccountService accountService) {
        this.pixKeyService = pixKeyService;
        this.pixService = pixService;
        this.accountService = accountService;
    }

    @GetMapping("/keys")
    @Operation(summary = "The signed-in user's Pix keys")
    public List<PixKeyResponse> keys(@AuthenticationPrincipal UserDetails principal) {
        return pixKeyService.list(principal.getUsername()).stream()
                .map(PixKeyResponse::from)
                .toList();
    }

    @PostMapping("/keys")
    @Operation(summary = "Register a Pix key; a RANDOM key is issued by the bank")
    public ResponseEntity<PixKeyResponse> registerKey(
            @AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody PixKeyRequest request) {
        PixKey key = pixKeyService.register(principal.getUsername(), request.type(), request.value());
        return ResponseEntity.status(HttpStatus.CREATED).body(PixKeyResponse.from(key));
    }

    @DeleteMapping("/keys/{id}")
    @Operation(summary = "Give up a Pix key")
    public ResponseEntity<Void> deleteKey(@AuthenticationPrincipal UserDetails principal,
                                          @PathVariable UUID id) {
        pixKeyService.delete(principal.getUsername(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/recipients")
    @Operation(summary = "Who a Pix key belongs to, for the confirmation screen")
    public PixRecipientResponse resolve(@AuthenticationPrincipal UserDetails principal,
                                        @Valid @RequestBody PixLookupRequest request) {
        return recipient(principal, pixKeyService.resolve(request.key()));
    }

    @PostMapping("/transfers")
    @Operation(summary = "Send money to a Pix key")
    public ResponseEntity<TransactionResponse> pay(@AuthenticationPrincipal UserDetails principal,
                                                   @Valid @RequestBody PixTransferRequest request) {
        var transaction = pixService.pay(principal.getUsername(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(TransactionResponse.from(transaction));
    }

    @PostMapping("/brcode")
    @Operation(summary = "Build a Pix code (copia e cola / QR) for one of the user's own keys")
    public BrCodeResponse brCode(@AuthenticationPrincipal UserDetails principal,
                                 @Valid @RequestBody BrCodeRequest request) {
        var result = pixService.brCodeFor(
                principal.getUsername(), request.keyId(), request.amount(), request.description());

        return new BrCodeResponse(result.payload(), PixKeyResponse.from(result.key()),
                result.amount(), result.description());
    }

    @PostMapping("/charges")
    @Operation(summary = "Create a shareable request to be paid, for a QR code or a link")
    public ResponseEntity<PixChargeResponse> createCharge(
            @AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody PixChargeRequest request) {
        var result = pixService.createCharge(
                principal.getUsername(), request.keyId(), request.amount(), request.description());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(PixChargeResponse.from(result.charge(), result.brCode()));
    }

    @GetMapping("/charges/{id}")
    @Operation(summary = "Open a shared payment link and say who it pays")
    public PixChargeLookupResponse openCharge(@AuthenticationPrincipal UserDetails principal,
                                              @PathVariable UUID id) {
        var charge = pixService.openCharge(id);

        return new PixChargeLookupResponse(recipient(principal, charge.getKey()),
                charge.getAmount(), charge.getDescription(), charge.getExpiresAt());
    }

    @PostMapping("/brcode/parse")
    @Operation(summary = "Read a pasted Pix code and say who it pays")
    public BrCodeParseResponse parseBrCode(@AuthenticationPrincipal UserDetails principal,
                                           @Valid @RequestBody BrCodeParseRequest request) {
        var parsed = pixService.parse(request.payload());

        return new BrCodeParseResponse(
                recipient(principal, parsed.key()), parsed.amount(), parsed.description());
    }

    /** Flags the caller's own keys, so the app can say so before they try to pay one. */
    private PixRecipientResponse recipient(UserDetails principal, PixKey key) {
        var myAccount = accountService.findByOwnerEmail(principal.getUsername());
        boolean own = key.getAccount().getId().equals(myAccount.getId());

        return PixRecipientResponse.from(key, own);
    }
}
