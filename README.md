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

The dev profile keeps its H2 database in `./data`, so an enrolled face and an
uploaded photo survive a restart. Delete that folder for a clean seeded database.

It seeds two accounts:

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
under `app.face.match-threshold`.

### Choosing that threshold

It was measured, not guessed. `tools/face-threshold-eval` in the web repo runs
face-api over 21 real faces and reports the distance between different people
(210 pairs — two faces in one photo are necessarily different people) against the
same face recaptured under camera-like variation. False accepts over those pairs:

| Threshold | Strangers accepted |
| --------- | ------------------ |
| 0.45      | 0 / 210            |
| 0.50      | 1 / 210            |
| 0.55      | 4 / 210            |
| 0.60      | 15 / 210           |

0.60 is face-api.js's documented general-purpose default and lets a stranger
through 7% of the time — acceptable for tagging photos in a gallery, not for
releasing money. The closest different-person pair measured 0.471, so the app
ships **0.45**.

Twenty-one faces is enough to rule a threshold out, nowhere near enough to claim
an error rate. A real deployment calibrates on thousands of pairs and tracks
accuracy per demographic group, since face recognition is well documented to
perform unevenly across them.

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

This is a portfolio demo, not a production identity check.

**It does tell people apart** — that is what the measurement above shows. What it
does not do is prove that a live human is present. Descriptor matching answers
"is this the enrolled face?", never "is this a real face, here, now?". Two
consequences:

1. A photograph or a video replay can defeat the browser-side liveness challenge.
2. More fundamentally, the descriptor is computed in the browser, so nothing
   forces an attacker to use the camera at all. Anyone holding a photo of the
   user can compute a matching descriptor offline and POST it straight to
   `/api/face/verify`. **The face here raises the cost of an attack; it is not a
   second factor in the sense a hardware key or a TOTP code is.**

Closing that gap means the server has to see the actual frames, which in practice
means a certified vendor (iBeta PAD Level 2 / ISO-IEC 30107-3): AWS Rekognition
Face Liveness, FaceTec, iProov, or in Brazil Unico, CAF or Serpro Datavalid.

Tokens live in memory, which suits a single instance; more than one node needs
Redis.

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
| `app.face.match-threshold` | `0.45`          | Max distance counted as a match |
| `CORS_ORIGINS`      | see below              | Allowed front-end origins     |
| `DATABASE_URL`      | —                      | JDBC URL (prod profile)       |
| `DATABASE_USER`     | —                      | DB user (prod profile)        |
| `DATABASE_PASSWORD` | —                      | DB password (prod profile)    |

Run the prod profile with `-Dspring-boot.run.profiles=prod`.

CORS uses **origin patterns**, not a fixed list. The dev profile allows any
localhost port plus the private ranges (`192.168.*`, `10.*`, `172.16.*`), so the
app can be opened from a phone on the same Wi-Fi without pinning whichever IP the
machine has today. Prod takes an explicit list from `CORS_ORIGINS`.

This matters more than it looks: a browser attaches an `Origin` header to every
request, and Spring answers a non-matching one with a bare `403 Invalid CORS
request` before the controller ever runs. `curl` sends no `Origin`, so an API
checked only by hand can look perfectly healthy while every browser is refused.
`CorsTest` covers it.
