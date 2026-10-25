# Implementation Plan — PayNest MVP

Derived from [product-spec.md](product-spec.md). Each step is small and ends with passing tests. Do not start a step before the previous one is green.

## Approach

- Single Spring Boot app (modular packages: `auth`, `wallet`, `transfer`, `web`).
- Specs first: if behaviour changes, update the spec and log it in [change-log.md](change-log.md) before changing code.
- Tests are written alongside each step, following [test-plan.md](test-plan.md).

## Proposed layout

```
paynest-mvp/
├── pom.xml / mvnw
├── docker-compose.yml          # PostgreSQL
└── src/
    ├── main/java/.../
    │   ├── auth/               # register, login, JWT
    │   ├── wallet/             # wallet, top-up
    │   ├── transfer/           # transfer, idempotency
    │   ├── transaction/        # history
    │   └── common/             # error handling
    ├── main/resources/
    │   ├── db/migration/       # Flyway SQL
    │   └── static/             # index/login/register/wallet pages
    └── test/java/...
```

## Steps

| # | Step | Deliverable | Done when |
|---|---|---|---|
| 0 | Project skeleton | Spring Initializr project (Web, Security, JPA, Validation, PostgreSQL, Flyway, Testcontainers), `docker-compose.yml` | App starts; health endpoint OK |
| 1 | Database schema | Flyway migration for `users`, `wallets`, `transactions` with constraints | Migration applies on a clean DB; `balance >= 0` check enforced |
| 2 | Register + login | F1, F2, BCrypt, JWT filter, error format | Auth tests pass (T-AUTH-*) |
| 3 | Wallet + top-up | F3, F4, rules R1, R2, R8 | Wallet tests pass (T-WAL-*) |
| 4 | Transfer | F5, rules R1–R5; single DB transaction; optimistic locking (`version`) | Transfer tests pass (T-TRF-*) |
| 5 | Idempotency | R7 via unique `(from_wallet_id, idempotency_key)` | Idempotency tests pass |
| 6 | History | F6 | History tests pass (T-HIS-*) |
| 7 | Concurrency check | 100 parallel transfers test | No negative balance; money conserved |
| 8 | Frontend | Three static pages calling the API | Manual walkthrough of all flows works |
| 9 | Wrap-up | README run instructions, final spec/change-log review | Fresh clone runs following README |

## Key design decisions

- **Atomicity (R5):** debit, credit and the transaction record in one `@Transactional` method.
- **No overdraw (R2):** DB check constraint + optimistic locking with retry on conflict; lock wallets in a consistent order (lower id first) to avoid deadlocks.
- **Idempotency (R7):** insert the transaction with the key; on unique-violation, return the existing transaction.
- **Money type:** `long` (VND), never floating point.
- **Auth:** stateless JWT, short expiry (e.g. 1 hour), no refresh token in the MVP.

## Risks

| Risk | Mitigation |
|---|---|
| Race conditions on balance | Concurrency test in step 7; DB constraint as last line of defence |
| Scope creep toward microservices | Out-of-scope list in the spec; changes go through the change-log |
| Frontend time sink | Plain HTML/JS only, no framework |

## Later (not now)

Split into services, add Kafka + Outbox, notifications, Kubernetes, per the README roadmap.
