package com.pedro.bank.repository;

import com.pedro.bank.domain.PixCharge;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PixChargeRepository extends JpaRepository<PixCharge, UUID> {

    /**
     * Opening a link has to reach the owner's name for the confirmation screen,
     * and open-in-view is disabled, so all three hops are fetched here.
     */
    @EntityGraph(attributePaths = {"key", "key.account", "key.account.owner"})
    Optional<PixCharge> findWithKeyById(UUID id);
}
