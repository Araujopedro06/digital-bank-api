package com.pedro.bank.service;

import com.pedro.bank.domain.Account;
import com.pedro.bank.domain.PixCharge;
import com.pedro.bank.domain.PixKey;
import com.pedro.bank.domain.Transaction;
import com.pedro.bank.domain.TransferRail;
import com.pedro.bank.repository.PixChargeRepository;
import com.pedro.bank.repository.PixKeyRepository;
import com.pedro.bank.web.dto.PixTransferRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Paying by Pix key, and the codes and links that carry one. The money itself is
 * moved by {@link AccountService} — a key is an address, not a different kind of
 * transfer.
 */
@Service
public class PixService {

    private final PixKeyService pixKeyService;
    private final PixKeyRepository pixKeyRepository;
    private final PixChargeRepository pixChargeRepository;
    private final AccountService accountService;
    private final String city;
    private final Duration chargeLifetime;

    public PixService(PixKeyService pixKeyService, PixKeyRepository pixKeyRepository,
                      PixChargeRepository pixChargeRepository, AccountService accountService,
                      @Value("${app.pix.city}") String city,
                      @Value("${app.pix.charge-lifetime}") Duration chargeLifetime) {
        this.pixKeyService = pixKeyService;
        this.pixKeyRepository = pixKeyRepository;
        this.pixChargeRepository = pixChargeRepository;
        this.accountService = accountService;
        this.city = city;
        this.chargeLifetime = chargeLifetime;
    }

    @Transactional
    public Transaction pay(String fromEmail, PixTransferRequest request) {
        PixKey key = pixKeyService.resolve(request.key());

        return accountService.transfer(fromEmail, key.getAccount(), request.amount(),
                request.description(), request.faceToken(), TransferRail.PIX);
    }

    /**
     * Builds a code for one of the caller's own keys. The key is looked up by id
     * and filtered by owner, so a code can never be issued for someone else's key
     * — that would be a request to send money to a stranger wearing the caller's
     * name.
     */
    @Transactional(readOnly = true)
    public BrCodeResult brCodeFor(String email, UUID keyId, BigDecimal amount, String description) {
        PixKey key = ownKey(email, keyId);

        String payload = BrCode.build(key.getValue(), key.getAccount().getOwner().getName(),
                city, amount, description);

        return new BrCodeResult(payload, key, amount, description);
    }

    @Transactional(readOnly = true)
    public ParsedPayment parse(String payload) {
        BrCode.Parsed parsed = BrCode.parse(payload);

        return new ParsedPayment(
                pixKeyService.resolve(parsed.key()), parsed.amount(), parsed.description());
    }

    /**
     * Creates the row a shareable link points at. Only the id goes into the link,
     * so the key itself never leaves this side.
     */
    @Transactional
    public ChargeResult createCharge(String email, UUID keyId, BigDecimal amount,
                                     String description) {
        PixKey key = ownKey(email, keyId);

        PixCharge charge = pixChargeRepository.save(
                new PixCharge(key, amount, description, Instant.now().plus(chargeLifetime)));

        // The same request in the other form, so one round trip gives the caller
        // both the link to put in a QR and the standard copia e cola string.
        String brCode = BrCode.build(key.getValue(), key.getAccount().getOwner().getName(),
                city, amount, description);

        return new ChargeResult(charge, brCode);
    }

    /**
     * Opens a link. An expired charge is reported as missing rather than as
     * expired: to whoever is holding the link, the two are the same dead end, and
     * saying which one it is only helps someone probing for valid ids.
     */
    @Transactional(readOnly = true)
    public PixCharge openCharge(UUID id) {
        return pixChargeRepository.findWithKeyById(id)
                .filter(charge -> !charge.hasExpired(Instant.now()))
                .orElseThrow(() -> new PixChargeNotFoundException(String.valueOf(id)));
    }

    /** One of the caller's own keys, by id. Anyone else's looks like none at all. */
    private PixKey ownKey(String email, UUID keyId) {
        Account account = accountService.findByOwnerEmail(email);

        return pixKeyRepository.findById(keyId)
                .filter(candidate -> candidate.getAccount().getId().equals(account.getId()))
                .orElseThrow(() -> new PixKeyNotFoundException(String.valueOf(keyId)));
    }

    public record BrCodeResult(String payload, PixKey key, BigDecimal amount, String description) {
    }

    public record ChargeResult(PixCharge charge, String brCode) {
    }

    public record ParsedPayment(PixKey key, BigDecimal amount, String description) {
    }
}
