# Digital Bank — API

REST backend for a digital bank: accounts, transfers and statements. Written in
Java 21 with Spring Boot 3, JWT authentication and a PostgreSQL/H2 schema managed
by Flyway.

The Angular client that consumes this API lives in
[digital-bank-web](https://github.com/Araujopedro06/digital-bank-web).

## Stack

| Concern        | Choice                                  |
| -------------- | --------------------------------------- |
| Language       | Java 21                                 |
| Framework      | Spring Boot 3.3 (Web, Data JPA, Security) |
| Authentication | JWT (HS384, stateless)                  |
| Database       | H2 in dev, PostgreSQL in prod           |
| Migrations     | Flyway                                  |
| Docs           | springdoc-openapi (Swagger UI)          |

## Layout

```
com.pedro.bank
├── domain       entities and business rules (Account.debit throws on overdraft)
├── repository   Spring Data JPA interfaces
├── service      use cases; the transfer is one atomic transaction
├── security     JWT issuing, the auth filter, UserDetails lookup
├── web          controllers, DTOs, the error handler
└── config       security, OpenAPI, dev seed data
```

## Running it

Requires JDK 21.

```bash
mvn spring-boot:run
```

The dev profile starts on an in-memory H2 database and seeds two accounts:

| E-mail          | Password   | Opening balance |
| --------------- | ---------- | --------------- |
| pedro@demo.com  | demo1234   | R$ 2.500,00     |
| maria@demo.com  | demo1234   | R$ 800,00       |

- API: <http://localhost:8080/api>
- Swagger UI: <http://localhost:8080/swagger-ui.html>
- H2 console: <http://localhost:8080/h2-console>

## Tests

```bash
mvn test
```

26 tests covering the transfer rules (balance moves, both ledger lines are
written, overdraft/self-transfer/unknown-account are rejected), the auth flow
(a login token unlocks the account endpoint; no token and a bad token are 401),
face matching (same face with capture drift matches, a different face does not,
malformed descriptors are rejected) and the step-up tokens (single use, bound to
one user and one purpose).

## Endpoints

| Method | Path                    | Auth | Purpose                               |
| ------ | ----------------------- | ---- | ------------------------------------- |
| POST   | `/api/auth/register`    | —    | Create user + open account            |
| POST   | `/api/auth/login`       | —    | Password step; JWT, or a face challenge |
| POST   | `/api/auth/login/face`  | —    | Trade challenge + face for a JWT      |
| GET    | `/api/accounts/me`      | JWT  | Account and balance                   |
| GET    | `/api/transactions`     | JWT  | Paged statement, newest first         |
| POST   | `/api/transfers`        | JWT  | Move money to another account         |
| POST   | `/api/deposits`         | JWT  | Add funds (demo funding rail)         |
| GET    | `/api/profile/photo`    | JWT  | Profile photo bytes, 404 if none      |
| POST   | `/api/profile/photo`    | JWT  | Upload JPEG/PNG, up to 2 MB           |
| DELETE | `/api/profile/photo`    | JWT  | Remove the photo                      |
| GET    | `/api/face/enrollment`  | JWT  | Whether a face is enrolled            |
| PUT    | `/api/face/enrollment`  | JWT  | Store the descriptor, with consent    |
| DELETE | `/api/face/enrollment`  | JWT  | Erase the biometric data              |
| POST   | `/api/face/verify`      | JWT  | Match a face → single-use transfer token |

## Facial verification

A user who enrols a face gets two extra checks: the face becomes a second factor
at login, and every transfer has to be confirmed with it.

**The image never reaches the server.** The browser turns the face into a
128-number descriptor and only that is sent. **The comparison, however, is done
here** — a client that decides its own match result is not a check at all. Two
descriptors count as the same person when the Euclidean distance between them is
under `app.face.match-threshold` (0.5; face-api.js's general default is 0.6,
tightened here to trade retries for fewer false accepts).

Both flows use single-use, two-minute tokens from `StepUpTokenService`:

```
login    POST /api/auth/login      → { requiresFaceVerification: true, challengeToken }
         POST /api/auth/login/face → { token: <JWT> }

transfer POST /api/face/verify     → { verificationToken }
         POST /api/transfers       → the token is spent here
```

The tests cover the ways around it: a missing token, a replayed token, a token
issued for another user, and a LOGIN token offered as a TRANSFER confirmation are
all rejected, and the balance is left untouched.

### Limits, stated plainly

This is a portfolio demo, not a production identity check. Descriptor matching
proves *similarity to an enrolled face*, not *liveness* — a photograph or a video
replay can defeat the browser-side challenge. Real deployments use a dedicated
anti-spoofing vendor (AWS Face Liveness, iProov, Unico). Tokens live in memory,
which suits one instance; more than one node would need Redis.

### LGPD

Biometric data is sensitive personal data (Lei 13.709/2018, art. 5, II), so:

- enrolment requires explicit consent (`consent: true`, validated with `@AssertTrue`)
  and the moment it was given is stored alongside the descriptor;
- only the descriptor is kept — never the captured image;
- `DELETE /api/face/enrollment` erases it on request (art. 18), and the account
  falls back to password-only.

## Notes on the design

- **Transfers are atomic.** Debit, credit and both ledger lines happen inside one
  `@Transactional` method, so a failure anywhere rolls back the whole operation.
- **Balances use `BigDecimal`,** never `double`, and the schema adds a
  `balance >= 0` check as a second line of defence.
- **Accounts carry a `@Version` column,** so two concurrent transfers on the same
  account make one fail rather than silently overwrite the other.
- **Error responses stay generic.** "Insufficient funds" does not echo back the
  balance or account number; the client decides the user-facing wording.

## Configuration

| Variable            | Default                | Meaning                       |
| ------------------- | ---------------------- | ----------------------------- |
| `JWT_SECRET`        | dev-only placeholder   | HMAC key, min. 32 bytes       |
| `app.face.match-threshold` | `0.5`           | Max distance counted as a match |
| `CORS_ORIGINS`      | `http://localhost:4200`| Allowed front-end origins     |
| `DATABASE_URL`      | —                      | JDBC URL (prod profile)       |
| `DATABASE_USER`     | —                      | DB user (prod profile)        |
| `DATABASE_PASSWORD` | —                      | DB password (prod profile)    |

Run the prod profile with `-Dspring-boot.run.profiles=prod`.
