package com.pedro.bank.service;

import com.pedro.bank.domain.ProfilePhoto;
import com.pedro.bank.domain.User;
import com.pedro.bank.repository.ProfilePhotoRepository;
import com.pedro.bank.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

@Service
public class ProfilePhotoService {

    public static final long MAX_BYTES = ProfilePhoto.MAX_BYTES;

    /**
     * Accepted formats, keyed by the magic bytes that actually start the file.
     * The declared Content-Type is attacker-controlled, so the real bytes decide.
     */
    private static final Map<String, byte[]> SIGNATURES = Map.of(
            "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF},
            "image/png", new byte[]{(byte) 0x89, 'P', 'N', 'G'});

    private final ProfilePhotoRepository photoRepository;
    private final UserRepository userRepository;

    public ProfilePhotoService(ProfilePhotoRepository photoRepository,
                               UserRepository userRepository) {
        this.photoRepository = photoRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public void upload(String email, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidProfilePhotoException("No file was sent");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new InvalidProfilePhotoException("Image must be 2 MB or smaller");
        }

        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException e) {
            throw new InvalidProfilePhotoException("Could not read the uploaded file");
        }

        String contentType = detectContentType(data);
        photoRepository.findByUserEmail(email).ifPresentOrElse(
                existing -> existing.replaceWith(contentType, data),
                () -> {
                    User user = userRepository.findByEmail(email).orElseThrow();
                    photoRepository.save(new ProfilePhoto(user, contentType, data));
                });
    }

    @Transactional(readOnly = true)
    public Optional<ProfilePhoto> find(String email) {
        return photoRepository.findByUserEmail(email);
    }

    @Transactional
    public void delete(String email) {
        photoRepository.deleteByUserEmail(email);
    }

    private String detectContentType(byte[] data) {
        return SIGNATURES.entrySet().stream()
                .filter(entry -> startsWith(data, entry.getValue()))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElseThrow(() -> new InvalidProfilePhotoException("Only JPEG and PNG are accepted"));
    }

    private boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
