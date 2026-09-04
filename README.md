# Digital Bank — API

REST backend for a digital bank: accounts, transfers, Pix, loans and statements.
Written in Java 21 with Spring Boot 3, JWT authentication and a PostgreSQL/H2
schema managed by Flyway.

**Live: <https://digital-bank-api-701x.onrender.com>** —
[Swagger UI](https://digital-bank-api-701x.onrender.com/swagger-ui.html). The
free tier sleeps when idle, so the first request after a quiet spell can take
~50 seconds.

The Angular client that consumes this API lives in
[digital-bank-web](https://github.com/Araujopedro06/digital-bank-web), deployed
at <https://tiny-druid-25e148.netlify.app>.

## Stack

| Concern        | Choice                                  |
| -------------- | --------------------------------------- |
| Language       | Java 21                                 |
| Framework      | Spring Boot 3.3 (Web, Data JPA, Security) |
| Authentication | JWT (HS384, stateless)                  |
| Database       | H2 in dev, PostgreSQL in prod           |
| Container      | Multi-stage Dockerfile, non-root runtime |
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

| E-mail          | Password   | Opening balance | Pix keys                        |
| --------------- | ---------- | --------------- | ------------------------------- |
| pedro@demo.com  | demo1234   | R$ 2.500,00     | e-mail, random                  |
| maria@demo.com  | demo1234   | R$ 800,00       | e-mail, `(11) 98765-4321`       |

Each side gets a key so a visitor can send a Pix without first inventing one.

- API: <http://localhost:8080/api>
- Swagger UI: <http://localhost:8080/swagger-ui.html>
- H2 console: <http://localhost:8080/h2-console>

## Tests

```bash
mvn test
```

98 tests covering the transfer rules (balance moves, both ledger lines are
written, overdraft/self-transfer/unknown-account are rejected), the auth flow
(a login token unlocks the account endpoint; no token and a bad token are 401),
face matching (same face with capture drift matches, a different face does not,
malformed descriptors are rejected), the step-up tokens (single use, bound to one
user and one purpose), Pix (keys normalise and stay unique, invalid CPFs and
foreign e-mails are refused, paying a key moves the money and is labelled as a
Pix, and an enrolled face is still required), payment links (an id that is random
rather than derived from the key, expiry pinned on both sides of the boundary, and
giving up a key killing the links that pointed at it), the BR Code (checksum
against the published check value, a single altered character rejected, accents
folded), the allowance (paid in full, trimmed, refused; a refusal costing no
cooldown), loans (the instalment against the Price formula worked out by hand,
every instalment leaving exactly zero owed, settling early costing less than the
instalments it replaces, and a payment with no balance changing nothing), and
CORS (every case sends an `Origin`, because that is the header curl omits and
browsers always send).

`PostgresSchemaTest` boots the whole application against a **real PostgreSQL**,
started by the test itself — no Docker daemon required, locally or in CI. The
other tests run on H2 in PostgreSQL mode, which is close but not identical: the
profile photo column already behaved differently between the two once. Without
this, the first time the migrations meet PostgreSQL would be during a
deployment.

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
| GET    | `/api/pix/keys`         | JWT  | The user's Pix keys                   |
| POST   | `/api/pix/keys`         | JWT  | Register a key; RANDOM is issued here |
| DELETE | `/api/pix/keys/{id}`    | JWT  | Give up a key                         |
| POST   | `/api/pix/recipients`   | JWT  | Resolve a key → who owns it           |
| POST   | `/api/pix/transfers`    | JWT  | Send money to a Pix key               |
| POST   | `/api/pix/brcode`       | JWT  | Build a copia e cola payload          |
| POST   | `/api/pix/brcode/parse` | JWT  | Read a pasted code and say who it pays |
| POST   | `/api/pix/charges`      | JWT  | Create a shareable request to be paid |
| GET    | `/api/pix/charges/{id}` | JWT  | Open a shared link and say who it pays |
| GET    | `/api/allowance`        | JWT  | Whether the aunt is taking calls      |
| POST   | `/api/allowance`        | JWT  | Ask her for money                     |
| GET    | `/api/loans/terms`      | JWT  | Ceiling, rate and instalment counts   |
| GET    | `/api/loans/simulation` | JWT  | What a loan would cost                |
| GET    | `/api/loans/active`     | JWT  | The running loan, or 204              |
| POST   | `/api/loans`            | JWT  | Take a loan, released immediately     |
| POST   | `/api/loans/active/payments`   | JWT | Pay one instalment             |
| POST   | `/api/loans/active/settlement` | JWT | Pay it off early               |

Two of those reads are POSTs, which looks wrong until you see what travels in
them: a Pix key is somebody's CPF, phone number or e-mail address, and query
strings end up in access logs, proxy logs and browser history. The body keeps
them out of all three.

## Getting money into an account

An empty account cannot be shown to anybody. Someone who has just signed up has a
zero balance, and every screen worth looking at needs money behind it — the Pix
form works perfectly and does nothing. Two ways in, both play money, and the
whole reason either can exist is that none of it is real.

### The rich aunt

`POST /api/allowance` with an amount. She pays it in full up to the generous
limit, pays the generous limit and no more up to the haggle limit, and refuses
outright above it.

The response is an **outcome**, never a sentence:

```json
{ "outcome": "HAGGLED", "asked": 1500.00, "granted": 500.00, … }
```

What she actually says lives in the client, for the same reason every other
user-facing string does — and it lets the app pick a different line each time,
which a joke needs and a status code cannot provide.

A grant starts a cooldown, read from the ledger rather than a table of its own:
the last `ALLOWANCE` line already records when she last gave. A refusal writes no
line, so being told to get a job does not also cost you the next five minutes.

### The loan

Approved on the spot, because there is nothing to underwrite. What is not
hand-waved is the arithmetic: a real **Price table**, `Amortization` in the domain
package, at twenty digits of working precision before rounding to centavos.

The design decision worth pointing at is what `outstanding` means. It is the
**principal still owed**, not the sum of the instalments left to pay:

| | after 1 of 12 on R$ 1.000 |
| --- | --- |
| Instalments left to pay | R$ 1.072,39 |
| `outstanding` | R$ 927,51 |

Interest that has not been charged yet is not a debt. Storing the debt that way
makes settling early cost exactly the remaining principal, with the months that
will now never happen never billed — which is what settling early is supposed to
mean, and would have taken a separate discount calculation under the other
representation.

Two details that only show up in the arithmetic:

- **The last instalment is whatever is actually left**, plus its interest, rather
  than the quoted figure. Rounding each instalment to centavos drifts over twelve
  months, and someone who has paid every instalment has to owe zero, not four
  cents. `payingEveryInstalmentLeavesNothingOwed` pins that.
- **The money is taken before the debt is reduced.** Rolling back would undo a
  failed payment in the database either way, but not in memory: the entity would
  stay mutated for the rest of the request, and anything reading it after the
  failure would see a debt that shrank without anyone paying.

A loan payment is not face-confirmed. The face guards *transfers* — money leaving
towards somebody else. An instalment goes to the lender, on a debt the borrower
already took on, and no bank asks for biometrics to collect one.

## Pix

A key is an alias for an account, so money can be sent without anyone passing
around account numbers.

| Type     | What is stored              | Typed as                          |
| -------- | --------------------------- | --------------------------------- |
| `CPF`    | 11 digits                   | `529.982.247-25` or `52998224725` |
| `PHONE`  | `+5511987654321`            | `(11) 98765-4321`, `11987654321`  |
| `EMAIL`  | the address, lower case     | the account's own e-mail          |
| `RANDOM` | a UUID the bank issues      | nothing — it is generated         |

**Normalising before storing is the point, not tidiness.** Without it
`529.982.247-25` and `52998224725` would be two different keys pointing at two
different accounts, and a payer would have no way to tell which one they were
about to pay. The unique index on the canonical value is what actually enforces
one owner per key.

An account holds at most five keys, and only one each of CPF, phone and e-mail —
those identify a person, so holding two would be claiming to be two people.

Paying resolves the key first and answers with the owner's **name**, which is what
makes the confirmation screen worth having. That also turns a phone number into a
person's name, so a real deployment rate-limits it; here it is open.

### Ownership, and what is not checked

An e-mail key must be the address the account was opened with. That is the only
possession this app can actually verify. A CPF is checked for valid check digits
and a phone for a plausible Brazilian format, and then both are taken at their
word — confirming either means a document check or an SMS, neither of which a
portfolio demo has any way to do. Real Pix verifies both before a key is issued.

### The code behind the QR

`BrCode` builds and parses the EMV® QR Code payload — the "Pix copia e cola"
string. It is a flat list of `IDVVdata` groups (two digits of identifier, two of
length, then that many characters), a few of which contain another such list,
ending in a CRC-16/CCITT-FALSE over everything before it. That checksum is why a
banking app can reject a code that lost a character passing through a chat
message, and `BrCodeTest` pins it against the algorithm's published check value
rather than only round-tripping through itself.

It is written out here rather than pulled from a library because it is a hundred
lines of string handling, and depending on someone else's build of it would hide
the one part worth understanding.

### The QR that actually scans

A BR Code is the standards-correct thing to put in a QR, and in this app nothing
can pay it: a real banking app reads the payload fine and then asks the DICT who
owns the key, which has never heard of it. So the QR on the *Receber* screen
encodes a **link back into this app** instead — scan it with any phone camera,
sign in, and the payment screen opens already knowing who is being paid. The
copia e cola payload is still offered next to it, for pasting into this app's own
*Pagar* screen.

That link carries a `pix_charges` row id and nothing else. The obvious shortcut
would have been to put the key straight in the URL:

```
                    ✗  /pix/pay?key=11987654321&amount=60
                    ✓  /pix/pay/1afc51ca-cdb1-498a-ac80-5b4ed836fb43
```

A payment link is made to be forwarded — pasted into WhatsApp, screenshotted,
opened weeks later. The first form spreads somebody's phone number or CPF through
every chat, access log and browser history it touches, and contradicts the reason
`/api/pix/recipients` is a POST in the first place. The id says nothing about who
is behind it, and the server answers what it means only to a signed-in caller.

Charges expire (`PIX_CHARGE_LIFETIME`, 24h by default) and an expired one is
reported as missing rather than as expired, since telling the two apart only
helps someone probing for live ids. Giving up a key deletes its charges with it,
through `ON DELETE CASCADE` — a QR on a wall must not keep resolving to an
address its owner has abandoned.

### What this is not

This is Pix-shaped, not Pix. Money moves between accounts **inside this bank
only**. Real Pix settles through the Banco Central's SPI, with an ISPB per
institution, keys registered in the central DICT directory rather than a local
table, and messaging and availability rules an application cannot opt into by
itself. What is faithful here is the shape a user sees — key types, the
confirmation screen, the BR Code payload a real app can scan — and the atomicity
underneath.

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

## Deploying

The image builds on the host, so Docker is not needed locally.

### Render

`render.yaml` is a Blueprint: **New > Blueprint**, point it at this repository,
and it creates the Postgres instance and the web service wired together. Set
`CORS_ORIGINS` to the deployed front end's exact origin once that is live;
everything else is filled in, including a generated `JWT_SECRET`.

Two free-tier facts worth knowing: a free web service sleeps after 15 minutes
idle, so the first request after a quiet spell pays a cold start, and Render's
free Postgres expires after a while and takes the data with it.

### Anywhere else

Any host that runs a container works. It needs:

| Variable                | Purpose                                              |
| ----------------------- | ---------------------------------------------------- |
| `SPRING_PROFILES_ACTIVE`| `prod`                                               |
| `DATABASE_URL`          | `postgres://…` or a `jdbc:postgresql://…` URL         |
| `JWT_SECRET`            | 32+ bytes, generated per environment                  |
| `CORS_ORIGINS`          | the front end's exact origin                          |
| `DEMO_SEED`             | `true` to seed the two demo accounts                  |
| `FACE_RETENTION_HOURS`  | `24` on a public demo, `0` to keep enrolments         |

`DATABASE_URL` is accepted in either form. Managed hosts hand out a
`postgres://user:pass@host/db` string, which JDBC cannot parse and which fails
with an error pointing nowhere near the cause, so
`DatabaseUrlEnvironmentPostProcessor` splits it before the datasource is built.

### Running it locally against Postgres

```bash
docker compose up --build
```

Postgres plus the API on the prod profile. Add `--profile web` to include the
front end, if that repository is cloned next to this one.

### Biometric data on a public demo

`FACE_RETENTION_HOURS` exists because a public demo collects real biometric data
from strangers who are trying a feature out. LGPD treats that as sensitive
personal data gathered for one purpose; keeping it past the visit serves nothing.
The retention job deletes enrolments older than the window, on top of the consent
that is already required and the delete that is already offered.


## Configuration

| Variable            | Default                | Meaning                       |
| ------------------- | ---------------------- | ----------------------------- |
| `JWT_SECRET`        | dev-only placeholder   | HMAC key, min. 32 bytes       |
| `app.face.match-threshold` | `0.45`          | Max distance counted as a match |
| `PIX_CITY`          | `SAO PAULO`            | Merchant city in generated Pix codes |
| `PIX_CHARGE_LIFETIME` | `24h`                | How long a shared payment link stays live |
| `ALLOWANCE_GENEROUS_LIMIT` | `500.00`        | Paid in full up to here       |
| `ALLOWANCE_HAGGLE_LIMIT` | `2000.00`         | Above here she refuses        |
| `ALLOWANCE_COOLDOWN` | `5m`                  | Wait between grants           |
| `LOAN_MONTHLY_RATE` | `0.025`                | Monthly interest              |
| `LOAN_MAX_PRINCIPAL` | `5000.00`             | Most that can be borrowed     |
| `LOAN_INSTALLMENTS` | `3,6,12,24`            | Instalment counts on offer    |
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
