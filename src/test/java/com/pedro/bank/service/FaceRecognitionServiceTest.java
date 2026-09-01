package com.pedro.bank.service;

import com.pedro.bank.web.dto.RegisterRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FaceRecognitionServiceTest {

    private static final String EMAIL = "face@test.com";

    @Autowired
    private AuthService authService;

    @Autowired
    private FaceRecognitionService faceRecognitionService;

    private double[] enrolled;

    @BeforeEach
    void registerUser() {
        authService.register(new RegisterRequest("Face User", EMAIL, "password123"));
        enrolled = descriptor(1);
        faceRecognitionService.enroll(EMAIL, enrolled);
    }

    @Test
    void theSameFaceMatches() {
        assertThat(faceRecognitionService.verify(EMAIL, enrolled).matched()).isTrue();
    }

    @Test
    void aSlightlyDifferentCaptureOfTheSameFaceStillMatches() {
        // Descriptors from two captures of one person drift by a small amount.
        double[] again = enrolled.clone();
        for (int i = 0; i < again.length; i++) {
            again[i] += 0.01;
        }

        var result = faceRecognitionService.verify(EMAIL, again);
        assertThat(result.distance()).isLessThan(result.threshold());
        assertThat(result.matched()).isTrue();
    }

    @Test
    void aDifferentFaceDoesNotMatch() {
        var result = faceRecognitionService.verify(EMAIL, descriptor(99));

        assertThat(result.distance()).isGreaterThan(result.threshold());
        assertThat(result.matched()).isFalse();
    }

    @Test
    void enrollingAgainReplacesTheStoredFace() {
        double[] replacement = descriptor(42);
        faceRecognitionService.enroll(EMAIL, replacement);

        assertThat(faceRecognitionService.verify(EMAIL, replacement).matched()).isTrue();
        assertThat(faceRecognitionService.verify(EMAIL, enrolled).matched()).isFalse();
    }

    @Test
    void deletingTheEnrollmentRemovesTheBiometricData() {
        faceRecognitionService.deleteEnrollment(EMAIL);

        assertThat(faceRecognitionService.isEnrolled(EMAIL)).isFalse();
        assertThatThrownBy(() -> faceRecognitionService.verify(EMAIL, enrolled))
                .isInstanceOf(FaceNotEnrolledException.class);
    }

    @Test
    void aDescriptorOfTheWrongLengthIsRejected() {
        assertThatThrownBy(() -> faceRecognitionService.verify(EMAIL, new double[]{0.1, 0.2}))
                .isInstanceOf(InvalidFaceDescriptorException.class);
    }

    @Test
    void aDescriptorContainingNaNIsRejected() {
        double[] poisoned = enrolled.clone();
        poisoned[0] = Double.NaN;

        assertThatThrownBy(() -> faceRecognitionService.verify(EMAIL, poisoned))
                .isInstanceOf(InvalidFaceDescriptorException.class);
    }

    /** A deterministic stand-in for a real 128-number face-api.js descriptor. */
    private double[] descriptor(long seed) {
        Random random = new Random(seed);
        double[] values = new double[128];
        for (int i = 0; i < values.length; i++) {
            values[i] = random.nextDouble() - 0.5;
        }
        return values;
    }
}
