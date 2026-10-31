# Test Plan — PayNest

Maps to [product-spec.md](product-spec.md) (requirements FR-*, invariants INV-*), the weekly exits in [implementation-plan.md](implementation-plan.md) and the deployment in [aws-setup.md](../aws-setup.md).

## Strategy

- **Integration tests with Testcontainers** (PostgreSQL, MongoDB, Kafka, Redis) are the main tool; constraints, locking and messaging are real, not mocked.
- **Unit tests** only for pure logic (state machine, validation).
- **Load/concurrency tests** with k6 or Gatling in `load-test/`.
- **Failure drills** (Kafka down, bank flaky) are scripted and documented.
- **AWS is never called from automated tests.** The CloudWatch client is a mock; everything that needs a real account is a manual T-AWS case.
- Each invariant INV-1 to INV-6 has at least one test.

## Week 1 — User and Wallet

| ID | Case | Expected |
|---|---|---|
| T-USR-1 | Register with valid data | User stored in MongoDB, password hashed |
| T-USR-2 | Register with duplicate email | Rejected (409) |
| T-USR-3 | Login correct / wrong password | JWT returned / 401 |
| T-USR-4 | KYC status default and update | PENDING by default; changes persist |
| T-WAL-1 | Wallet exists with balance 0 for new user | OK |
| T-WAL-2 | Top-up adds amount | Balance increases; transaction recorded |
| T-WAL-3 | Withdraw within balance | Balance decreases |
| T-WAL-4 | Withdraw more than balance | Rejected; balance unchanged (INV-1) |
| T-WAL-5 | Amount 0 or negative | 400 validation error |
| T-WAL-6 | Protected endpoint without/with bad JWT | 401 |
| T-WAL-7 | Direct DB update to negative balance | Blocked by check constraint |
| T-WAL-8 | Two concurrent updates on one wallet | Optimistic lock conflict handled; no lost update |

## Week 2 — Transfer and Ledger

| ID | Case | Expected |
|---|---|---|
| T-TRF-1 | A sends to B with enough balance | 202 + id; ends COMPLETED; balances correct |
| T-TRF-2 | Insufficient funds | Ends FAILED; no balance change (INV-1) |
| T-TRF-3 | Same `Idempotency-Key` twice | One transfer; same result returned (INV-3) |
| T-TRF-4 | Same key sent concurrently (10x) | Exactly one transfer |
| T-TRF-5 | Missing `Idempotency-Key` | 400 |
| T-TRF-6 | Self-transfer / unknown recipient | Rejected with clear error code |
| T-TRF-7 | State machine transitions | Only allowed transitions per spec §5 |
| T-TRF-8 | Failure after hold (ledger down) | COMPENSATING, then FAILED; hold released |
| T-TRF-9 | Get status of unknown / other user's transfer | 404 / 403 |
| T-LED-1 | Posted transfer creates debit + credit | Entries sum to zero (INV-4) |
| T-LED-2 | Update/delete a ledger entry | Not possible (immutable) |
| T-LED-3 | Same posting requested twice | Posted once |
| T-CON-1 | 1,000 concurrent transfers from shared wallets | No negative balance (INV-1) |
| T-CON-2 | Sum of balances before vs after the load | Unchanged (INV-2) |
| T-CON-3 | Opposite concurrent transfers A↔B | No deadlock |

## Week 3 — Kafka, Outbox, Saga, Bank

| ID | Case | Expected |
|---|---|---|
| T-OUT-1 | Business change and outbox row | Written in one transaction; rollback removes both |
| T-OUT-2 | Relay publishes pending rows | Event on Kafka with correct key and envelope; row marked sent |
| T-OUT-3 | Kafka down, then up | Events delivered after recovery; none lost (INV-5) |
| T-OUT-4 | Two relay instances | No double-claim (`SKIP LOCKED`) |
| T-EVT-1 | Event envelope | Has `eventId`, `eventType`, `occurredAt`, `version` |
| T-EVT-2 | Duplicate event delivered | Handled once (INV-6) |
| T-EVT-3 | Poison message | Retries with backoff, then lands in DLT |
| T-EVT-4 | Permanent error type | Skips retries, goes to DLT |
| T-EVT-5 | Events for one wallet | Processed in order (same key/partition) |
| T-SAG-1 | Happy path across services via events | COMPLETED |
| T-SAG-2 | Failure at each step (hold, ledger, capture) | Correct compensation; money conserved (INV-2) |
| T-SAG-3 | Service restarts mid-saga | Saga resumes and finishes |
| T-BNK-1 | Bank times out on top-up | Retried within limits; clear failure after exhaustion |
| T-BNK-2 | Bank fails randomly (many runs) | No duplicate credit, no lost money |
| T-DRL-1 | **Drill**: stop Kafka during load, restart | All events eventually delivered; invariants hold |

## Week 4 — Notification, Gateway, Kubernetes

| ID | Case | Expected |
|---|---|---|
| T-NTF-1 | `TransferCompleted` event | Both parties get a notification, stored as SENT |
| T-NTF-2 | `TransferFailed` event | Sender notified |
| T-NTF-3 | Same event twice | One notification |
| T-NTF-4 | Sender temporary failure | Goes to retry topic, then DLT after attempts |
| T-NTF-5 | List, mark read, unread-count | Counts and flags correct |
| T-NTF-6 | Preferences change | Respected; cached in Redis and refreshed on update |
| T-GW-1 | No / invalid JWT | Rejected at gateway |
| T-GW-2 | Rate limit exceeded | 429 |
| T-GW-3 | Routing to each service | Correct service reached |
| T-K8S-1 | `kubectl apply -f k8s/ -n paynest` | All pods Running and Ready |
| T-K8S-2 | Kill a pod | Replacement starts; traffic continues |
| T-K8S-3 | Probes | Startup/readiness/liveness respond; liveness does not depend on DB/Kafka |
| T-K8S-4 | ConfigMap / Secret usage | Config injected; no secrets in images or repo |
| T-CI-1 | CI workflow on push | Build + tests run and pass |
| T-E2E-1 | Register two users, top up, transfer, check notifications | Whole flow works on the cluster |

## AWS deployment and CloudWatch

Local runs must not change: every case below applies only with the `aws` profile or `infra/docker-compose.aws.yml`, unless it says otherwise.

### Metrics (automated)

| ID | Case | Expected |
|---|---|---|
| T-MET-1 | Start without the `aws` profile | No CloudWatch beans; Prometheus endpoint still works |
| T-MET-2 | Register meters with the `aws` profile | CloudWatch gets only `paynest.*`, `http.server.requests` and heap `jvm.memory.used`, without the tags `exception`, `method`, `outcome`, `error`; Prometheus still gets every meter |
| T-MET-3 | Publish | Namespace `PayNest`, dimension `application` = service name |
| T-MET-4 | Transfer completes | `paynest.transfer.completed` +1 and one `paynest.transfer.latency` sample; nothing counted before `FundsCaptured` |
| T-MET-5 | Transfer fails on hold, or after compensation | `paynest.transfer.failed` +1 with `reason` = failure code; for compensation, only after `FundsReleased` |
| T-MET-6 | Replayed reply, or saga step rolled back | Not counted again (metrics are recorded after commit) |

### Logs (automated)

| ID | Case | Expected |
|---|---|---|
| T-LOG-1 | Log a line with the `aws` profile | One ECS JSON object per line, with `message`, `log.level`, `service.name` and the MDC field `transferId` |
| T-LOG-2 | Log a line without the `aws` profile | Plain text, as before |

### Deployment (run with Docker, no AWS account)

| ID | Case | Expected |
|---|---|---|
| T-OPS-1 | `docker compose config` on both compose files with `--profile apps` | Only the gateway publishes a port; all containers have `mem_limit`, total under 8 GB; services use `awslogs` and the `aws` profile |
| T-OPS-2 | Same command without `infra/.env` | Refused: `JWT_SECRET` and the database passwords are required |
| T-OPS-3 | Whole system with the `aws` profile and the memory limits, CloudWatch replaced by a local fake endpoint; run `scripts/e2e.sh` | E2E passes; no container OOM-killed; each service publishes to namespace `PayNest`; a transfer's `transferId` appears in the JSON logs of transfer, wallet and ledger services |
| T-OPS-4 | `docker compose stop` during a request | Request finishes; services exit within `stop_grace_period`, not killed (exit code 143, not 137) |
| T-OPS-5 | Regression: `docker compose -f infra/docker-compose.yml --profile apps up`, then `scripts/e2e.sh` | Passes with plain text logs and no AWS configuration |

### On EC2 (manual, needs an AWS account)

Follow [aws-setup.md](../aws-setup.md). These cost money while the instance runs; do the clean-up of §10 afterwards.

| ID | Case | Expected |
|---|---|---|
| T-AWS-1 | Security Group, from another network | Ports 8080 and 22 closed outside My IP; from My IP, only 8080 answers (not 5433, 9092, 27017, 6379, 8081-8086) |
| T-AWS-2 | Instance credentials | No access key on the instance; services and the Docker log driver authenticate with `paynest-ec2-role` |
| T-AWS-3 | Container logs | Log group `/paynest/app` has one stream per service; retention set to 7 days; infrastructure containers are not in it |
| T-AWS-4 | Logs Insights query on a `transferId` from `scripts/e2e.sh` | Returns lines from transfer, wallet and ledger services, in order |
| T-AWS-5 | Secrets in logs | Query for `password`, `Bearer`, `accessToken` and the JWT secret after an E2E run returns nothing |
| T-AWS-6 | Namespace `PayNest` after an E2E run | `paynest.transfer.completed.count`, `paynest.transfer.failed.count`, `paynest.transfer.latency.*`, `http.server.requests.*`, `jvm.memory.used` present, per service |
| T-AWS-7 | Namespace `CWAgent` | `mem_used_percent` and `disk_used_percent` present |
| T-AWS-8 | Alarm test: `aws cloudwatch set-alarm-state` on each alarm | SNS email arrives at `paynest-alerts` |
| T-AWS-9 | Metric filter `{ $.log.level = "ERROR" }`, after forcing an error | `PayNest/Logs` `ErrorCount` goes up |
| T-AWS-10 | Reboot the instance | All containers return (`restart: unless-stopped`); balances intact (INV-2) |
| T-AWS-11 | Custom metric count, after the E2E run | `aws cloudwatch list-metrics --namespace PayNest` count recorded; expected cost matches the budget |

## Invariant coverage

| Invariant | Tests |
|---|---|
| INV-1 | T-WAL-4, T-WAL-7, T-TRF-2, T-CON-1 |
| INV-2 | T-CON-2, T-SAG-2, T-BNK-2 |
| INV-3 | T-TRF-3, T-TRF-4 |
| INV-4 | T-LED-1, T-LED-2 |
| INV-5 | T-OUT-3, T-DRL-1 |
| INV-6 | T-EVT-2, T-NTF-3 |

## Exit criteria

- Week 1: T-USR-*, T-WAL-* green.
- Week 2: T-TRF-*, T-LED-*, T-CON-* green; load result recorded.
- Week 3: T-OUT-*, T-EVT-*, T-SAG-*, T-BNK-* green; Kafka drill passes.
- Week 4: T-NTF-*, T-GW-*, T-K8S-*, T-CI-1, T-E2E-1 green.
- AWS code: T-MET-*, T-LOG-*, T-OPS-* green.
- AWS deployment: T-AWS-* checked once on a real instance; instance stopped or terminated afterwards.
