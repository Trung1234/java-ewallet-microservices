# Product Spec — PayNest (E-Wallet Microservices)

Status: Draft v0.2 · Source of truth: [README.md](../README.md)

## 1. Summary

PayNest is a learning-project e-wallet built as Java / Spring Boot microservices. Users register, hold a wallet, top up / withdraw (against a simulated bank), transfer money to each other, and get notified. The goal is to practice Kafka, Transactional Outbox, Saga and Kubernetes. **No real money is processed.**

## 2. Users

- **Registered user**: owns one wallet (single currency, integer minor units).
- **Client**: Postman or a simple app calling the API gateway. No UI is required by the roadmap.

## 3. Services and responsibilities

| Service | DB | Responsibility |
|---|---|---|
| api-gateway | — | Single entry point, JWT validation, rate limiting |
| user-service | MongoDB | Register, login, KYC |
| wallet-service | PostgreSQL | Balance, hold, top-up (deposit), withdraw |
| transfer-service | PostgreSQL | Transfer orchestration (Saga orchestrator), idempotency |
| ledger-service | PostgreSQL | Immutable double-entry ledger |
| bank-adapter | — | Fake bank; randomly fails / times out on purpose |
| notification-service | MongoDB | Multi-channel notifications (email, push, in-app) |

Principle: **database per service**. No service reads another service's database.

Shared infra: Apache Kafka (KRaft), PostgreSQL, MongoDB, Redis (cache, lock).

## 4. Functional requirements

| ID | Requirement | Service |
|---|---|---|
| FR-1 | Register and log in; receive a JWT | user |
| FR-2 | Basic KYC status on a user (e.g. PENDING / VERIFIED) | user |
| FR-3 | Gateway rejects requests without a valid JWT and rate-limits clients | gateway |
| FR-4 | View wallet balance | wallet |
| FR-5 | Top up wallet (via bank-adapter) | wallet, bank |
| FR-6 | Withdraw from wallet (via bank-adapter) | wallet, bank |
| FR-7 | Create a transfer between two wallets with an `Idempotency-Key`; returns `202 Accepted` + `transferId` | transfer |
| FR-8 | Transfer runs as a Saga: hold → ledger post → capture + credit; compensate on failure | transfer, wallet, ledger |
| FR-9 | Query transfer status | transfer |
| FR-10 | Every posted transfer produces a balanced double-entry (debit A, credit B) | ledger |
| FR-11 | Services publish domain events via Transactional Outbox | wallet, transfer, ledger |
| FR-12 | Notification service consumes events and notifies both parties on `TransferCompleted` / `TransferFailed` | notification |
| FR-13 | Notification API: list, mark read, unread count, update preferences | notification |
| FR-14 | Failed event processing goes through retry topics, then a DLT | notification |

## 5. Transfer flow and states

Happy path (from README §2): client → `POST /transfers` → transfer PENDING → wallet **hold** on A → ledger posts (debit A, credit B) → wallet **capture** A and **credit** B → transfer COMPLETED + outbox event → Kafka → notifications. On hold failure or mid-flow error: FAILED, with compensation (release hold) when needed.

```
PENDING -> HELD -> COMPLETED
PENDING -> FAILED                 (insufficient funds)
HELD    -> COMPENSATING -> FAILED (error mid-flow, funds returned)
```

## 6. Invariants (must always hold; each needs a test)

- INV-1: A balance is never negative.
- INV-2: Total money in the system is unchanged by any internal transfer.
- INV-3: The same `Idempotency-Key` creates at most one transfer.
- INV-4: Ledger entries are immutable and every transaction balances (sum of debits = sum of credits).
- INV-5: No domain event is lost if Kafka is down (outbox keeps it until delivered).
- INV-6: A duplicated event never causes a duplicated side effect (idempotent consumers).

## 7. Events

Common envelope: `eventId`, `eventType`, `occurredAt`, `version`, `payload`.

| Event | Producer | Consumers |
|---|---|---|
| `TransferCompleted` | transfer | notification, ledger |
| `TransferFailed` | transfer | notification |
| Wallet events (hold, capture, credit) | wallet | transfer, ledger |
| Ledger posted | ledger | transfer |

Topic example: `wallet.transfer.events`. Message key: `walletId` or `transferId` to preserve ordering per aggregate. Producers use `acks=all` and `enable.idempotence=true`.

## 8. Outbox

Each producing service owns an `outbox` table (`id`, `aggregatetype`, `aggregateid`, `type`, `payload` JSONB, `created_at`) written **in the same DB transaction** as the business change. A relay publishes rows to Kafka. Start with a polling publisher (`@Scheduled` + `FOR UPDATE SKIP LOCKED`); Debezium CDC is an optional upgrade. Delivery is at-least-once, so consumers must be idempotent.

## 9. API (draft, via gateway)

| Method | Path | Purpose |
|---|---|---|
| POST | `/auth/register`, `/auth/login` | FR-1 |
| GET | `/wallets/me` | FR-4 |
| POST | `/wallets/me/topup`, `/wallets/me/withdraw` | FR-5, FR-6 |
| POST | `/transfers` (header `Idempotency-Key`) | FR-7 |
| GET | `/transfers/{id}` | FR-9 |
| GET | `/notifications`, `/notifications/unread-count` | FR-13 |
| PATCH | `/notifications/{id}/read` | FR-13 |
| PUT | `/preferences` | FR-13 |

Errors use `{ "code": "...", "message": "..." }`.

## 10. Non-functional requirements

- **Concurrency**: 1,000 concurrent transfers must leave no negative balance (optimistic locking).
- **Resilience**: bank-adapter failures/timeouts are handled by retry and compensation; Kafka outage does not lose events.
- **Deployability**: whole system runs with `docker compose` locally and `kubectl apply` on a local cluster (kind).
- **Observability**: Actuator health probes (liveness / readiness / startup), basic metrics and logs, monitoring in week 4.
- **Probe rule**: liveness must not check DB or Kafka.
- **Security**: passwords hashed; secrets in Kubernetes Secrets; JWT validated at the gateway.

## 11. Repo layout

As in README §7: `services/`, `libs/common-events`, `infra/` (docker-compose, debezium), `k8s/`, `load-test/`, `.github/workflows/`.

## 12. Success criteria (per README roadmap)

| Week | Criterion |
|---|---|
| 1 | User + Wallet work with JWT, top-up/withdraw; Testcontainers integration tests pass |
| 2 | Transfer + Ledger work with idempotency; 1,000 concurrent requests, no negative balance |
| 3 | Kafka, Outbox, Saga, flaky bank-adapter; stop Kafka mid-run, restart, no event lost |
| 4 | Notification, Kubernetes, CI/CD, monitoring; `kubectl apply` brings up the whole system |

## 13. Out of scope

Real payments, real KYC providers, multi-currency, a polished UI, multi-region deployment.

## 14. Open questions

- Money representation: integer minor units (assumed) vs decimal?
- Is withdraw required in week 1, or only top-up first?
- Polling publisher or Debezium for the outbox relay? (Default: polling first.)
- Auth ownership: does user-service issue the JWT and the gateway only validate it? (Default: yes.)
