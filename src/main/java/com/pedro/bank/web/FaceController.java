package com.pedro.bank.web;

import com.pedro.bank.repository.FaceEnrollmentRepository;
import com.pedro.bank.security.StepUpTokenService;
import com.pedro.bank.service.FaceRecognitionService;
import com.pedro.bank.service.FaceVerificationFailedException;
import com.pedro.bank.web.dto.FaceDescriptorRequest;
import com.pedro.bank.web.dto.FaceEnrollmentRequest;
import com.pedro.bank.web.dto.FaceStatusResponse;
import com.pedro.bank.web.dto.StepUpTokenResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The captured image never reaches this controller — the browser turns the face
 * into a 128-number descriptor and only that is sent. The comparison itself is
 * done here rather than in the browser, because a client that decides its own
 * match result is not a check at all.
 */
@RestController
@RequestMapping("/api/face")
@Tag(name = "Face verification")
@SecurityRequirement(name = "bearerAuth")
public class FaceController {

    private final FaceRecognitionService faceRecognitionService;
    private final FaceEnrollmentRepository enrollmentRepository;
    private final StepUpTokenService stepUpTokenService;

    public FaceController(FaceRecognitionService faceRecognitionService,
                          FaceEnrollmentRepository enrollmentRepository,
                          StepUpTokenService stepUpTokenService) {
        this.faceRecognitionService = faceRecognitionService;
        this.enrollmentRepository = enrollmentRepository;
        this.stepUpTokenService = stepUpTokenService;
    }

    @GetMapping("/enrollment")
    @Operation(summary = "Whether the signed-in user has a face enrolled")
    public FaceStatusResponse status(@AuthenticationPrincipal UserDetails principal) {
        return enrollmentRepository.findByUserEmail(principal.getUsername())
                .map(enrollment -> new FaceStatusResponse(true, enrollment.getConsentedAt()))
                .orElseGet(() -> new FaceStatusResponse(false, null));
    }

    @PutMapping("/enrollment")
    @Operation(summary = "Store or replace the face descriptor, with explicit consent")
    public FaceStatusResponse enroll(@AuthenticationPrincipal UserDetails principal,
                                     @Valid @RequestBody FaceEnrollmentRequest request) {
        faceRecognitionService.enroll(principal.getUsername(), request.descriptor());
        return status(principal);
    }

    @DeleteMapping("/enrollment")
    @Operation(summary = "Withdraw consent and erase the biometric data (LGPD art. 18)")
    public ResponseEntity<Void> deleteEnrollment(@AuthenticationPrincipal UserDetails principal) {
        faceRecognitionService.deleteEnrollment(principal.getUsername());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/verify")
    @Operation(summary = "Match a face and receive a single-use token for confirming a transfer")
    public StepUpTokenResponse verify(@AuthenticationPrincipal UserDetails principal,
                                      @Valid @RequestBody FaceDescriptorRequest request) {
        String email = principal.getUsername();
        if (!faceRecognitionService.verify(email, request.descriptor()).matched()) {
            throw new FaceVerificationFailedException();
        }

        return new StepUpTokenResponse(
                stepUpTokenService.issue(email, StepUpTokenService.Purpose.TRANSFER),
                stepUpTokenService.lifetimeSeconds());
    }
}
