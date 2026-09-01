package com.pedro.bank.service;

import com.pedro.bank.domain.FaceEnrollment;
import com.pedro.bank.domain.User;
import com.pedro.bank.repository.FaceEnrollmentRepository;
import com.pedro.bank.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FaceRecognitionService {

    private final FaceEnrollmentRepository enrollmentRepository;
    private final UserRepository userRepository;
    private final double matchThreshold;

    public FaceRecognitionService(FaceEnrollmentRepository enrollmentRepository,
                                  UserRepository userRepository,
                                  @Value("${app.face.match-threshold}") double matchThreshold) {
        this.enrollmentRepository = enrollmentRepository;
        this.userRepository = userRepository;
        this.matchThreshold = matchThreshold;
    }

    @Transactional(readOnly = true)
    public boolean isEnrolled(String email) {
        return enrollmentRepository.existsByUserEmail(email);
    }

    @Transactional
    public void enroll(String email, double[] descriptor) {
        requireWellFormed(descriptor);

        String serialized = FaceDescriptor.serialize(descriptor);
        enrollmentRepository.findByUserEmail(email).ifPresentOrElse(
                existing -> existing.replaceWith(serialized),
                () -> {
                    User user = userRepository.findByEmail(email).orElseThrow();
                    enrollmentRepository.save(new FaceEnrollment(user, serialized));
                });
    }

    /**
     * @return how far the candidate is from the enrolled face, and whether that
     *         clears the configured threshold
     */
    @Transactional(readOnly = true)
    public MatchResult verify(String email, double[] candidate) {
        requireWellFormed(candidate);

        FaceEnrollment enrollment = enrollmentRepository.findByUserEmail(email)
                .orElseThrow(FaceNotEnrolledException::new);

        double distance = FaceDescriptor.distance(
                FaceDescriptor.deserialize(enrollment.getDescriptor()), candidate);
        return new MatchResult(distance <= matchThreshold, distance, matchThreshold);
    }

    /** LGPD art. 18: the user can withdraw consent and have the biometric data erased. */
    @Transactional
    public void deleteEnrollment(String email) {
        enrollmentRepository.deleteByUserEmail(email);
    }

    private void requireWellFormed(double[] descriptor) {
        if (!FaceDescriptor.isWellFormed(descriptor)) {
            throw new InvalidFaceDescriptorException();
        }
    }

    public record MatchResult(boolean matched, double distance, double threshold) {
    }
}
