package com.pedro.bank.web;

import com.pedro.bank.service.AccountService;
import com.pedro.bank.web.dto.AccountResponse;
import com.pedro.bank.web.dto.DepositRequest;
import com.pedro.bank.web.dto.TransactionResponse;
import com.pedro.bank.web.dto.TransferRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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

@RestController
@RequestMapping("/api")
@Tag(name = "Accounts")
@SecurityRequirement(name = "bearerAuth")
public class AccountController {

    private static final int MAX_PAGE_SIZE = 100;

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping("/accounts/me")
    @Operation(summary = "The signed-in user's account and balance")
    public AccountResponse myAccount(@AuthenticationPrincipal UserDetails principal) {
        return AccountResponse.from(accountService.findByOwnerEmail(principal.getUsername()));
    }

    @GetMapping("/transactions")
    @Operation(summary = "Statement for the signed-in user, newest first")
    public Page<TransactionResponse> statement(
            @AuthenticationPrincipal UserDetails principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE));
        return accountService.statement(principal.getUsername(), pageable)
                .map(TransactionResponse::from);
    }

    @PostMapping("/transfers")
    @Operation(summary = "Move money to another account")
    public ResponseEntity<TransactionResponse> transfer(
            @AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody TransferRequest request) {
        var transaction = accountService.transfer(principal.getUsername(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(TransactionResponse.from(transaction));
    }

    @PostMapping("/deposits")
    @Operation(summary = "Add funds to the signed-in user's account (demo rail)")
    public ResponseEntity<TransactionResponse> deposit(
            @AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody DepositRequest request) {
        var transaction = accountService.deposit(principal.getUsername(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(TransactionResponse.from(transaction));
    }
}
