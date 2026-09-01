package com.pedro.bank.repository;

import com.pedro.bank.domain.Account;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    Optional<Account> findByNumber(String number);

    /**
     * The owner is fetched eagerly here: with open-in-view disabled, the response
     * DTO is built after the transaction closes and would otherwise hit a
     * detached lazy proxy.
     */
    @EntityGraph(attributePaths = "owner")
    Optional<Account> findByOwnerEmail(String email);

    boolean existsByNumber(String number);
}
