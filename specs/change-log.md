# Change Log — Specs

Record every change to the specs here, **before** changing code. Newest first.

Entry format:

```
## YYYY-MM-DD — short title
- Changed: which spec(s) and what
- Why: reason
- Impact: affected plan steps / tests
```

---

## 2026-10-25 — Initial specs
- Changed: created `product-spec.md`, `implementation-plan.md`, `test-plan.md`, `change-log.md`.
- Why: start spec-driven development of a simple PayNest web app, based on README.md.
- Impact: MVP scoped to a single Spring Boot app (register/login, wallet, top-up, transfer, history). Microservices, Kafka, Outbox, Saga and Kubernetes from the README are deferred. No code written yet.
