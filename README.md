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

Covers the transfer rules (balance moves, both ledger lines are written,
overdraft/self-transfer/unknown-account are rejected) and the auth flow
(a login token unlocks the account endpoint; no token and a bad token are 401).

## Endpoints

| Method | Path                 | Auth | Purpose                      |
| ------ | -------------------- | ---- | ---------------------------- |
| POST   | `/api/auth/register` | —    | Create user + open account   |
| POST   | `/api/auth/login`    | —    | Exchange credentials for JWT |
| GET    | `/api/accounts/me`   | JWT  | Account and balance          |
| GET    | `/api/transactions`  | JWT  | Paged statement, newest first |
| POST   | `/api/transfers`     | JWT  | Move money to another account |
| POST   | `/api/deposits`      | JWT  | Add funds (demo funding rail) |

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
| `CORS_ORIGINS`      | `http://localhost:4200`| Allowed front-end origins     |
| `DATABASE_URL`      | —                      | JDBC URL (prod profile)       |
| `DATABASE_USER`     | —                      | DB user (prod profile)        |
| `DATABASE_PASSWORD` | —                      | DB password (prod profile)    |

Run the prod profile with `-Dspring-boot.run.profiles=prod`.
