package com.pedro.bank.repository;

import com.pedro.bank.domain.ProfilePhoto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProfilePhotoRepository extends JpaRepository<ProfilePhoto, UUID> {

    Optional<ProfilePhoto> findByUserEmail(String email);

    void deleteByUserEmail(String email);
}
