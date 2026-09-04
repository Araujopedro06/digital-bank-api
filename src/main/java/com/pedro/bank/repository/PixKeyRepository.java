package com.pedro.bank.repository;

import com.pedro.bank.domain.PixKey;
import com.pedro.bank.domain.PixKeyType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PixKeyRepository extends JpaRepository<PixKey, UUID> {

    /**
     * Resolving a key has to reach the owner's name for the confirmation screen,
     * and open-in-view is disabled, so both hops are fetched here.
     */
    @EntityGraph(attributePaths = {"account", "account.owner"})
    Optional<PixKey> findByValue(String value);

    List<PixKey> findByAccountIdOrderByCreatedAt(UUID accountId);

    long countByAccountId(UUID accountId);

    boolean existsByAccountIdAndType(UUID accountId, PixKeyType type);

    boolean existsByValue(String value);
}
