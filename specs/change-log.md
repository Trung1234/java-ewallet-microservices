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

## 2026-10-31 — Add AWS EC2 deployment and CloudWatch monitoring
- Changed: `architect.md` gains the `observability` library, `TransferMetrics` and an AWS deployment section. `test-plan.md` gains the T-MET, T-LOG, T-OPS and T-AWS cases.
- Why: `aws-setup.md` describes running the system on one EC2 instance with logs and metrics in CloudWatch. Local runs must stay as they are, so everything is behind the `aws` Spring profile and `infra/docker-compose.aws.yml`.
- Impact: new module `libs/observability`, used by all seven services. Business metrics in transfer-service. `transferId` in the logs of the saga services. No API, schema or event change. The T-AWS cases are manual and need a real AWS account.

## 2026-10-31 — Add class diagrams from the source code
- Changed: added `architect.md` with Mermaid class diagrams for every library and service, derived from the current code.
- Why: there was no class-level view of the system; the README only covers services and flows.
- Impact: documentation only. No plan steps or tests change.

## 2026-10-25 — Rebase specs on the full README architecture
- Changed: rewrote `product-spec.md`, `implementation-plan.md`, `test-plan.md` to follow README.md: seven services, Kafka, Transactional Outbox, Saga, Kubernetes, and the 4-week roadmap.
- Why: the first draft (v0.1) reduced scope to a single-app MVP, but the intended project is the microservices system described in README.md.
- Impact: the plan is now four weekly phases with exit checks; the test plan maps tests to invariants INV-1 to INV-6. The single-app MVP scope is dropped.

## 2026-10-25 — Initial specs (v0.1, superseded)
- Changed: created the four spec files for a simplified single-app MVP.
- Why: first interpretation of the request.
- Impact: replaced by the entry above. No code written.
