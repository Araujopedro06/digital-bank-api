package com.pedro.bank.web;

import com.pedro.bank.service.AllowanceService;
import com.pedro.bank.web.dto.AllowanceRequest;
import com.pedro.bank.web.dto.AllowanceResponse;
import com.pedro.bank.web.dto.AllowanceStatusResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Play money for the demo, dressed as a rich aunt.
 *
 * <p>An empty account cannot be shown to anyone: a visitor who has just signed up
 * has nothing to send. This is the funding rail that fixes that, and it is only
 * defensible because none of the money here is real.
 */
@RestController
@RequestMapping("/api/allowance")
@Tag(name = "Allowance")
@SecurityRequirement(name = "bearerAuth")
public class AllowanceController {

    private final AllowanceService allowanceService;

    public AllowanceController(AllowanceService allowanceService) {
        this.allowanceService = allowanceService;
    }

    @GetMapping
    @Operation(summary = "Whether the aunt is taking calls, and what she is good for")
    public AllowanceStatusResponse status(@AuthenticationPrincipal UserDetails principal) {
        return AllowanceStatusResponse.from(allowanceService.status(principal.getUsername()));
    }

    @PostMapping
    @Operation(summary = "Ask her for money; she may grant it, trim it, or refuse")
    public AllowanceResponse ask(@AuthenticationPrincipal UserDetails principal,
                                 @Valid @RequestBody AllowanceRequest request) {
        return AllowanceResponse.from(
                allowanceService.request(principal.getUsername(), request.amount()));
    }
}
