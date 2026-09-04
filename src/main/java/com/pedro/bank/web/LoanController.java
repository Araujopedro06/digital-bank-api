package com.pedro.bank.web;

import com.pedro.bank.service.LoanService;
import com.pedro.bank.web.dto.LoanPaymentResponse;
import com.pedro.bank.web.dto.LoanQuoteResponse;
import com.pedro.bank.web.dto.LoanRequest;
import com.pedro.bank.web.dto.LoanResponse;
import com.pedro.bank.web.dto.LoanTermsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * An account may run one loan at a time, so the endpoints that act on it need no
 * id — there is only ever one thing they could mean, and nothing to guess at.
 */
@RestController
@RequestMapping("/api/loans")
@Tag(name = "Loans")
@SecurityRequirement(name = "bearerAuth")
public class LoanController {

    private final LoanService loanService;

    public LoanController(LoanService loanService) {
        this.loanService = loanService;
    }

    @GetMapping("/terms")
    @Operation(summary = "What is on offer: ceiling, rate and instalment counts")
    public LoanTermsResponse terms() {
        return LoanTermsResponse.from(loanService.terms());
    }

    @GetMapping("/simulation")
    @Operation(summary = "What a loan would cost, before taking it")
    public LoanQuoteResponse simulate(@RequestParam BigDecimal amount,
                                      @RequestParam int installments) {
        return LoanQuoteResponse.from(loanService.simulate(amount, installments));
    }

    @GetMapping("/active")
    @Operation(summary = "The loan currently running, or 204 when there is none")
    public ResponseEntity<LoanResponse> active(@AuthenticationPrincipal UserDetails principal) {
        return loanService.active(principal.getUsername())
                .map(loan -> ResponseEntity.ok(LoanResponse.from(loan)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping
    @Operation(summary = "Every loan this account has taken, newest first")
    public List<LoanResponse> history(@AuthenticationPrincipal UserDetails principal) {
        return loanService.history(principal.getUsername()).stream()
                .map(LoanResponse::from)
                .toList();
    }

    @PostMapping
    @Operation(summary = "Take a loan; approved on the spot and released immediately")
    public ResponseEntity<LoanResponse> take(@AuthenticationPrincipal UserDetails principal,
                                             @Valid @RequestBody LoanRequest request) {
        var loan = loanService.take(principal.getUsername(), request.amount(), request.installments());
        return ResponseEntity.status(HttpStatus.CREATED).body(LoanResponse.from(loan));
    }

    @PostMapping("/active/payments")
    @Operation(summary = "Pay one instalment")
    public LoanPaymentResponse payInstallment(@AuthenticationPrincipal UserDetails principal) {
        var result = loanService.payInstallment(principal.getUsername());
        return LoanPaymentResponse.from(result.payment(), result.loan());
    }

    @PostMapping("/active/settlement")
    @Operation(summary = "Pay the loan off early, for the remaining principal only")
    public LoanPaymentResponse settle(@AuthenticationPrincipal UserDetails principal) {
        var result = loanService.settle(principal.getUsername());
        return LoanPaymentResponse.from(result.payment(), result.loan());
    }
}
