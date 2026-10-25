# Implementation Plan — PayNest (4 Weeks)

Derived from [product-spec.md](product-spec.md) and README §8. Each week has a verification goal; do not start the next week until the previous one is verified. Spec changes go into [change-log.md](change-log.md) first.

## Working rules

- Spec first, then tests, then code. Tests are defined in [test-plan.md](test-plan.md).
- Java 21, Spring Boot, Maven wrapper per service.
- Each service owns its database; no cross-DB reads.
- Small steps, each ending green (build + tests).

## Overview

| Week | Theme | Services touched | Verification |
|---|---|---|---|
| 1 | Foundation: User + Wallet, JWT, top-up/withdraw | user, wallet, infra | Testcontainers integration tests |
| 2 | Money movement: Transfer + Ledger, idempotency, locking | transfer, ledger, wallet | 1,000 concurrent requests, no negative balance |
| 3 | Events and reliability: Kafka, Outbox, Saga, flaky bank | all + bank-adapter | Stop Kafka mid-run, restart, no event lost |
| 4 | Platform: Notification, Kubernetes, CI/CD, monitoring | notification, gateway, k8s | `kubectl apply` runs the whole system |

---

## Week 1 — User + Wallet

Goal: a user can register, log in, see a balance, top up and withdraw.

| Day | Tasks |
|---|---|
| 1 | Create repo skeleton (`services/`, `libs/`, `infra/`). `infra/docker-compose.yml` with PostgreSQL, MongoDB, Redis. |
| 2 | `user-service`: register/login, BCrypt, MongoDB, JWT issuing, KYC status field. |
| 3 | `wallet-service`: schema (`wallets`, `wallet_transactions`), create wallet for new user, get balance. |
| 4 | Top-up and withdraw; `balance >= 0` check constraint; `version` column for optimistic locking. |
| 5 | Minimal JWT validation in wallet-service (gateway comes in week 4). |
| 6 | Testcontainers integration tests for user and wallet. |
| 7 | Buffer / review against spec; update change-log. |

Open point: how a wallet is created for a new user before events exist (week 1: direct call or lazy creation on first access; replaced by an event in week 3).

Exit: tests T-USR-*, T-WAL-* pass.

---

## Week 2 — Transfer + Ledger

Goal: correct, idempotent, concurrency-safe transfers (still synchronous HTTP between services).

| Day | Tasks |
|---|---|
| 1 | `ledger-service`: schema for immutable double-entry; post endpoint; balance check per transaction. |
| 2 | `wallet-service`: hold / release / capture / credit operations with optimistic locking and retry. |
| 3 | `transfer-service`: schema, state machine (PENDING, HELD, COMPLETED, COMPENSATING, FAILED). |
| 4 | `POST /transfers` with `Idempotency-Key` (unique constraint), returns 202 + `transferId`; `GET /transfers/{id}`. |
| 5 | Orchestration: hold, ledger post, capture + credit; compensation on failure (HTTP calls with timeouts). |
| 6 | Redis lock / cache where useful (e.g. idempotency fast path). Load test with k6/Gatling: 1,000 concurrent transfers. |
| 7 | Fix concurrency issues found; verify invariants INV-1 to INV-4. |

Exit: T-TRF-*, T-LED-*, T-CON-* pass; load test shows no negative balance.

---

## Week 3 — Kafka, Outbox, Saga, Bank adapter

Goal: replace fragile synchronous coupling with events; survive failures.

| Day | Tasks |
|---|---|
| 1 | Add Kafka (KRaft) to compose. `libs/common-events` with the event envelope (`eventId`, `eventType`, `occurredAt`, `version`). |
| 2 | Outbox table + writer in wallet, transfer, ledger (same transaction as business change). |
| 3 | Outbox polling relay (`@Scheduled`, `SKIP LOCKED`); producer `acks=all`, idempotence on; keys by `walletId` / `transferId`. |
| 4 | Convert transfer orchestration to an event-driven Saga with compensation paths. |
| 5 | `bank-adapter`: fake bank with random failures and timeouts; wire top-up/withdraw through it with retry + timeout. |
| 6 | Idempotent consumers (`processed_events`); retry topics + DLT. |
| 7 | Failure drill: stop Kafka mid-run, restart, confirm no event lost and no duplicate effect. Optional: Debezium CDC variant. |

Exit: T-OUT-*, T-SAG-*, T-BNK-*, T-EVT-* pass; Kafka-outage drill documented.

---

## Week 4 — Notification, Kubernetes, CI/CD, Monitoring

Goal: complete system, deployable with one command.

| Day | Tasks |
|---|---|
| 1 | `notification-service`: consume transfer events, MongoDB storage, idempotency, templates, preference cache in Redis. |
| 2 | Notification API (list, read, unread-count, preferences); retry + DLT handling. |
| 3 | `api-gateway`: JWT validation, routing, rate limiting. |
| 4 | Dockerfiles per service; `k8s/` manifests: Deployments, Services, ConfigMaps, Secrets, Ingress, probes (startup / readiness / liveness), HPA. |
| 5 | Run on kind: `kubectl apply -f k8s/ -n paynest`; fix config issues. |
| 6 | `.github/workflows/`: build, test, build images. Monitoring: Actuator metrics + basic dashboard/logs. |
| 7 | End-to-end run-through, final spec / change-log review, README run instructions. |

Exit: T-NTF-*, T-K8S-*, T-E2E-* pass; whole system up via `kubectl apply`.

---

## Cross-cutting decisions

- **Saga style**: orchestration in transfer-service (per README).
- **Locking**: optimistic (`version`) with retry; DB check constraint as last defence.
- **Money**: integer minor units, never floating point.
- **Consumers**: always idempotent; outbox is not exactly-once.
- **Liveness probes** never check DB / Kafka.

## Risks

| Risk | Mitigation |
|---|---|
| Scope too large for 4 weeks | Each week has a hard exit; cut Debezium, HPA tuning or extra channels first |
| Local RAM limits (many containers) | Run only needed infra per week; set Docker resource limits |
| Saga compensation bugs | Test every failure branch explicitly (test plan) |
| Duplicate / out-of-order events | Keys for ordering, `processed_events`, idempotent handlers |

## Definition of done (per task)

Spec updated if behaviour changed, tests written and passing, change-log entry added when the spec moved.
