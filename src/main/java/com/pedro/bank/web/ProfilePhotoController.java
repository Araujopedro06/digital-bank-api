package com.pedro.bank.web;

import com.pedro.bank.domain.ProfilePhoto;
import com.pedro.bank.service.ProfilePhotoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;

@RestController
@RequestMapping("/api/profile/photo")
@Tag(name = "Profile photo")
@SecurityRequirement(name = "bearerAuth")
public class ProfilePhotoController {

    private final ProfilePhotoService photoService;

    public ProfilePhotoController(ProfilePhotoService photoService) {
        this.photoService = photoService;
    }

    @GetMapping
    @Operation(summary = "The signed-in user's profile photo, or 404 if there is none")
    public ResponseEntity<byte[]> get(@AuthenticationPrincipal UserDetails principal) {
        return photoService.find(principal.getUsername())
                .map(this::asResponse)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload or replace the profile photo (JPEG or PNG, up to 2 MB)")
    public ResponseEntity<Void> upload(@AuthenticationPrincipal UserDetails principal,
                                       @RequestParam("file") MultipartFile file) {
        photoService.upload(principal.getUsername(), file);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    @Operation(summary = "Remove the profile photo")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal UserDetails principal) {
        photoService.delete(principal.getUsername());
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<byte[]> asResponse(ProfilePhoto photo) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(photo.getContentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePrivate())
                .eTag(Long.toString(photo.getUpdatedAt().toEpochMilli()))
                .body(photo.getData());
    }
}
