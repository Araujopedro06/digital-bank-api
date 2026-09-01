package com.pedro.bank.repository;

import com.pedro.bank.domain.FaceEnrollment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface FaceEnrollmentRepository extends JpaRepository<FaceEnrollment, UUID> {

    Optional<FaceEnrollment> findByUserEmail(String email);

    boolean existsByUserEmail(String email);

    void deleteByUserEmail(String email);
}
