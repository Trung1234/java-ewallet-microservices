# Product Spec — PayNest MVP

Status: Draft v0.1

## 1. Summary

PayNest MVP is a small web app where users hold a virtual wallet, top it up with fake money, and transfer money to other users. It is a learning project: no real money.

The README describes a full microservices architecture (Kafka, Outbox, Saga, Kubernetes). The MVP deliberately **does not** build that. It delivers the core wallet behaviour in one Spring Boot app so it can be specified, built and tested quickly. The microservices design stays as a later evolution.

## 2. Users

- **Registered user**: has one wallet in a single currency (VND, integer amounts).

No admin role in the MVP.

## 3. In scope (MVP)

| ID | Feature | Description |
|---|---|---|
| F1 | Register | Email + password creates a user and an empty wallet. |
| F2 | Login / logout | Email + password; session via JWT. |
| F3 | View wallet | Shows current balance. |
| F4 | Top up | Adds a fake amount to own wallet (simulates bank deposit). |
| F5 | Transfer | Send an amount to another user, identified by email. |
| F6 | History | List of own transactions (top-ups, sent, received), newest first. |

## 4. Out of scope (MVP)

Kafka, Outbox, Saga, Kubernetes, API gateway, KYC, withdrawals, notifications, multi-currency, admin tools, password reset, rate limiting, mobile app.

## 5. User flows

1. **Sign up** → Login → Wallet page with balance 0.
2. **Top up**: enter amount → balance increases → entry appears in history.
3. **Transfer**: enter recipient email + amount → confirm → sender balance decreases, recipient increases → both see an entry in history.

## 6. Pages

| Page | Content |
|---|---|
| `/register` | Email, password form |
| `/login` | Email, password form |
| `/wallet` | Balance, top-up form, transfer form, history table |

## 7. Business rules

- R1: Amounts are positive integers (VND, no decimals).
- R2: Balance never goes negative.
- R3: A transfer to oneself is rejected.
- R4: A transfer to an unknown email is rejected.
- R5: A transfer is atomic: debit and credit both happen, or neither does.
- R6: Total money across all wallets changes only through top-ups.
- R7: Each transfer request carries an `Idempotency-Key`; repeating the same key creates only one transfer and returns the original result.
- R8: Top-up limit: max 10,000,000 per request (keeps the fake-money demo sane).
- R9: Emails are unique (case-insensitive). Passwords are at least 8 characters and stored hashed (BCrypt).

## 8. API (draft)

| Method | Path | Auth | Purpose |
|---|---|---|---|
| POST | `/api/auth/register` | — | F1 |
| POST | `/api/auth/login` | — | F2, returns JWT |
| GET | `/api/wallet` | JWT | F3 |
| POST | `/api/wallet/topup` | JWT | F4, body `{amount}` |
| POST | `/api/transfers` | JWT | F5, header `Idempotency-Key`, body `{toEmail, amount}` |
| GET | `/api/transactions` | JWT | F6 |

Errors use a common shape: `{ "code": "INSUFFICIENT_FUNDS", "message": "..." }`.
Error codes: `VALIDATION_ERROR`, `EMAIL_TAKEN`, `INVALID_CREDENTIALS`, `INSUFFICIENT_FUNDS`, `RECIPIENT_NOT_FOUND`, `SELF_TRANSFER`, `UNAUTHORIZED`.

## 9. Data model (draft)

- `users(id, email unique, password_hash, created_at)`
- `wallets(id, user_id unique, balance bigint check >= 0, version, created_at)`
- `transactions(id, type TOPUP|TRANSFER, from_wallet_id null, to_wallet_id, amount, idempotency_key null, created_at)`; unique on `(from_wallet_id, idempotency_key)`.

## 10. Tech choices (MVP)

- Backend: Java 21, Spring Boot, Spring Security (JWT), Spring Data JPA.
- Database: PostgreSQL (docker compose).
- Frontend: simple static HTML + vanilla JS served by Spring Boot (no separate SPA build).
- Tests: JUnit 5, Testcontainers.

## 11. Non-functional (MVP level)

- Money correctness over performance: concurrent transfers must not overdraw a wallet.
- Runs locally with `docker compose up` + `./mvnw spring-boot:run`.
- No sensitive data in logs; never log passwords or tokens.

## 12. Success criteria

- A new user can register, top up, transfer to a second user, and see correct balances and history.
- All business rules R1–R9 are covered by automated tests.
- 100 concurrent transfers from one wallet never produce a negative balance.

## 13. Open questions

- Keep VND-only or allow decimals later?
- Is a JWT in `localStorage` acceptable for a learning app, or use an HttpOnly cookie? (Default: JWT in memory/localStorage for simplicity.)
