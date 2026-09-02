package com.pedro.bank.service;

import com.pedro.bank.repository.FaceEnrollmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Deletes enrolled faces older than a configured age.
 *
 * <p>Exists for the public demo. Anyone who tries it enrols real biometric data,
 * which under LGPD is sensitive personal data collected for one purpose —
 * showing how the feature works. Keeping it past that visit serves nothing and
 * would be a genuine liability, so the demo sets a short window and the data
 * removes itself. Consent is still asked for, and deleting on request still
 * works; this is the backstop for the people who simply close the tab.
 *
 * <p>Disabled by default ({@code app.face.retention-hours: 0}), since a real
 * bank keeps the enrolment for as long as the account uses it.
 */
@Service
public class FaceRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(FaceRetentionJob.class);

    private final FaceEnrollmentRepository enrollmentRepository;
    private final long retentionHours;

    public FaceRetentionJob(FaceEnrollmentRepository enrollmentRepository,
                            @Value("${app.face.retention-hours}") long retentionHours) {
        this.enrollmentRepository = enrollmentRepository;
        this.retentionHours = retentionHours;
    }

    @Scheduled(fixedDelayString = "PT15M", initialDelayString = "PT1M")
    @Transactional
    public void deleteExpiredEnrollments() {
        if (retentionHours <= 0) {
            return;
        }

        Instant cutoff = Instant.now().minus(retentionHours, ChronoUnit.HOURS);
        int deleted = enrollmentRepository.deleteByUpdatedAtBefore(cutoff);
        if (deleted > 0) {
            log.info("Deleted {} face enrolment(s) older than {}h", deleted, retentionHours);
        }
    }
}
