package com.pedro.bank.service;

import com.pedro.bank.domain.Account;
import com.pedro.bank.domain.PixKey;
import com.pedro.bank.domain.PixKeyType;
import com.pedro.bank.repository.PixKeyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class PixKeyService {

    /** What the real thing allows a personal account, and a sane cap regardless. */
    public static final int MAX_KEYS = 5;

    private final PixKeyRepository pixKeyRepository;
    private final AccountService accountService;

    public PixKeyService(PixKeyRepository pixKeyRepository, AccountService accountService) {
        this.pixKeyRepository = pixKeyRepository;
        this.accountService = accountService;
    }

    @Transactional(readOnly = true)
    public List<PixKey> list(String email) {
        Account account = accountService.findByOwnerEmail(email);
        return pixKeyRepository.findByAccountIdOrderByCreatedAt(account.getId());
    }

    @Transactional
    public PixKey register(String email, PixKeyType type, String rawValue) {
        Account account = accountService.findByOwnerEmail(email);

        if (pixKeyRepository.countByAccountId(account.getId()) >= MAX_KEYS) {
            throw new PixKeyLimitReachedException(MAX_KEYS);
        }
        // A CPF, a phone and an e-mail each identify one person, so holding two
        // of the same kind would mean claiming to be two people. Random keys are
        // just handles and may be held up to the overall limit.
        if (type != PixKeyType.RANDOM && pixKeyRepository.existsByAccountIdAndType(account.getId(), type)) {
            throw new PixKeyAlreadyRegisteredException();
        }

        String value = PixKeyFormat.normalize(type, rawValue, account.getOwner().getEmail());
        if (pixKeyRepository.existsByValue(value)) {
            throw new PixKeyAlreadyRegisteredException();
        }

        return pixKeyRepository.save(new PixKey(account, type, value));
    }

    @Transactional
    public void delete(String email, UUID id) {
        Account account = accountService.findByOwnerEmail(email);

        // Filtering by owner rather than checking afterwards: a key belonging to
        // someone else must look exactly like a key that does not exist.
        PixKey key = pixKeyRepository.findById(id)
                .filter(candidate -> candidate.getAccount().getId().equals(account.getId()))
                .orElseThrow(() -> new PixKeyNotFoundException(String.valueOf(id)));

        pixKeyRepository.delete(key);
    }

    /**
     * Finds the account behind a key someone typed, so the payer can be shown who
     * they are about to pay before any money moves.
     *
     * <p>This does turn a phone number or an e-mail into a person's name, which is
     * exactly what makes the confirmation step useful and also what makes it worth
     * rate-limiting in a real deployment — otherwise it is an enumeration oracle.
     */
    @Transactional(readOnly = true)
    public PixKey resolve(String rawKey) {
        String value = PixKeyFormat.normalizeForLookup(rawKey);

        return pixKeyRepository.findByValue(value)
                .orElseThrow(() -> new PixKeyNotFoundException(value));
    }
}
