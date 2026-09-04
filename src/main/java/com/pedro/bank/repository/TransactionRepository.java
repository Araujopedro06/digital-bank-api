package com.pedro.bank.repository;

import com.pedro.bank.domain.Transaction;
import com.pedro.bank.domain.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    Page<Transaction> findByAccountIdOrderByCreatedAtDesc(UUID accountId, Pageable pageable);

    /**
     * The ledger is where the aunt's cooldown is read from — the last time she
     * gave, rather than a separate table saying so.
     */
    Optional<Transaction> findFirstByAccountIdAndTypeOrderByCreatedAtDesc(
            UUID accountId, TransactionType type);
}
