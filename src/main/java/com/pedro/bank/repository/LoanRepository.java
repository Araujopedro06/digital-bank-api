package com.pedro.bank.repository;

import com.pedro.bank.domain.Loan;
import com.pedro.bank.domain.LoanStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LoanRepository extends JpaRepository<Loan, UUID> {

    Optional<Loan> findByAccountIdAndStatus(UUID accountId, LoanStatus status);

    List<Loan> findByAccountIdOrderByCreatedAtDesc(UUID accountId);
}
