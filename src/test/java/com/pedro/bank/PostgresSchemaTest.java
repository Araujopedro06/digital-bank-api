package com.pedro.bank;

import com.pedro.bank.service.AccountService;
import com.pedro.bank.service.AuthService;
import com.pedro.bank.service.FaceRecognitionService;
import com.pedro.bank.service.ProfilePhotoService;
import com.pedro.bank.web.dto.DepositRequest;
import com.pedro.bank.web.dto.RegisterRequest;
import com.pedro.bank.web.dto.TransferRequest;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots the whole application against a real PostgreSQL.
 *
 * <p>Every other test runs on H2 in PostgreSQL mode, which is close but not the
 * same thing: the profile photo column already behaved differently between the
 * two once, and that only surfaced because Hibernate validates the schema. The
 * database in production is PostgreSQL, so something has to actually run there —
 * otherwise the first time the migrations meet it is during a deployment.
 *
 * <p>Deliberately exercises the parts where the two engines diverge most: the
 * bytea column behind the profile photo, UUID keys, NUMERIC money and the
 * timestamp columns.
 */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "app.demo.seed=false",
        "app.face.retention-hours=0",
        "app.face.match-threshold=0.45",
        "app.jwt.secret=postgres-schema-test-secret-key-at-least-32-bytes",
        "app.jwt.expiration=2h",
        "app.cors.allowed-origins=http://localhost:4200",
})
class PostgresSchemaTest {

    private static final EmbeddedPostgres POSTGRES = start();

    private static EmbeddedPostgres start() {
        try {
            return EmbeddedPostgres.start();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not start the embedded PostgreSQL", e);
        }
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @AfterAll
    static void stop() throws IOException {
        POSTGRES.close();
    }

    @Autowired
    private AuthService authService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private ProfilePhotoService photoService;

    @Autowired
    private FaceRecognitionService faceRecognitionService;

    /**
     * Reaching this method at all is most of the point: the context only starts
     * if Flyway applied both migrations to PostgreSQL and Hibernate then agreed
     * that every mapping matches the resulting schema.
     */
    @Test
    void theSchemaMigratesAndValidatesOnPostgres() {
        assertThat(authService).isNotNull();
    }

    @Test
    void moneyMovesCorrectlyOnPostgres() {
        authService.register(new RegisterRequest("PG Sender", "pg-sender@test.com", "password123"));
        authService.register(new RegisterRequest("PG Receiver", "pg-receiver@test.com", "password123"));
        accountService.deposit("pg-sender@test.com",
                new DepositRequest(new BigDecimal("100.00"), "Opening"));

        String destination = accountService.findByOwnerEmail("pg-receiver@test.com").getNumber();
        accountService.transfer("pg-sender@test.com",
                new TransferRequest(destination, new BigDecimal("30.50"), "Rent", null));

        assertThat(accountService.findByOwnerEmail("pg-sender@test.com").getBalance())
                .isEqualByComparingTo("69.50");
        assertThat(accountService.findByOwnerEmail("pg-receiver@test.com").getBalance())
                .isEqualByComparingTo("30.50");
    }

    /** The bytea column: this is the mapping that differed between H2 and PostgreSQL. */
    @Test
    void aProfilePhotoRoundTripsThroughPostgres() {
        authService.register(new RegisterRequest("PG Photo", "pg-photo@test.com", "password123"));

        byte[] png = pngBytes();
        photoService.upload("pg-photo@test.com",
                new MockMultipartFile("file", "avatar.png", "image/png", png));

        var stored = photoService.find("pg-photo@test.com").orElseThrow();
        assertThat(stored.getContentType()).isEqualTo("image/png");
        assertThat(stored.getData()).isEqualTo(png);
        assertThat(stored.getSizeBytes()).isEqualTo(png.length);
    }

    /** The 128-number descriptor is stored as text; check it survives the round trip intact. */
    @Test
    void aFaceEnrolmentRoundTripsThroughPostgres() {
        authService.register(new RegisterRequest("PG Face", "pg-face@test.com", "password123"));

        double[] descriptor = descriptor();
        faceRecognitionService.enroll("pg-face@test.com", descriptor);

        var result = faceRecognitionService.verify("pg-face@test.com", descriptor);
        assertThat(result.matched()).isTrue();
        assertThat(result.distance()).isZero();
    }

    private byte[] pngBytes() {
        byte[] header = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
        byte[] png = new byte[64];
        System.arraycopy(header, 0, png, 0, header.length);
        new Random(7).nextBytes(new byte[0]);
        for (int i = header.length; i < png.length; i++) {
            png[i] = (byte) i;
        }
        return png;
    }

    private double[] descriptor() {
        Random random = new Random(11);
        double[] values = new double[128];
        for (int i = 0; i < values.length; i++) {
            values[i] = random.nextDouble() - 0.5;
        }
        return values;
    }
}
