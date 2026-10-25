# Test Plan — PayNest MVP

Maps to rules and features in [product-spec.md](product-spec.md).

## Strategy

- **Integration tests first**: Spring Boot test + Testcontainers PostgreSQL, calling the real HTTP API. Most behaviour is DB-dependent (constraints, locking), so mocking the DB is avoided.
- **Unit tests** only for small pure logic (validation, amount rules).
- **Manual check** for the frontend (MVP).
- Every business rule R1–R9 has at least one test.

## Test cases

### Auth (F1, F2, R9)

| ID | Case | Expected |
|---|---|---|
| T-AUTH-1 | Register with valid email/password | 201, user + wallet (balance 0) created |
| T-AUTH-2 | Register with existing email (different case) | 409 `EMAIL_TAKEN` |
| T-AUTH-3 | Register with password < 8 chars or bad email | 400 `VALIDATION_ERROR` |
| T-AUTH-4 | Password stored hashed | DB value is not the plain password |
| T-AUTH-5 | Login with correct credentials | 200 + JWT |
| T-AUTH-6 | Login with wrong password | 401 `INVALID_CREDENTIALS` |
| T-AUTH-7 | Call protected endpoint without / with bad token | 401 `UNAUTHORIZED` |

### Wallet and top-up (F3, F4, R1, R2, R8)

| ID | Case | Expected |
|---|---|---|
| T-WAL-1 | Get wallet after register | Balance 0 |
| T-WAL-2 | Top up 100,000 | Balance 100,000; TOPUP transaction recorded |
| T-WAL-3 | Top up 0, negative, or non-integer | 400 `VALIDATION_ERROR` |
| T-WAL-4 | Top up above 10,000,000 | 400 `VALIDATION_ERROR` |
| T-WAL-5 | Direct DB update to negative balance | Rejected by check constraint |

### Transfer (F5, R1–R6)

| ID | Case | Expected |
|---|---|---|
| T-TRF-1 | A (500) sends 200 to B (0) | A=300, B=200; one TRANSFER transaction |
| T-TRF-2 | Send more than balance | 422 `INSUFFICIENT_FUNDS`; balances unchanged |
| T-TRF-3 | Send exact full balance | Succeeds; sender balance 0 |
| T-TRF-4 | Send to own email | 400 `SELF_TRANSFER` |
| T-TRF-5 | Send to unknown email | 404 `RECIPIENT_NOT_FOUND` |
| T-TRF-6 | Amount 0 / negative | 400 `VALIDATION_ERROR` |
| T-TRF-7 | Failure after debit (simulated exception) | Whole transfer rolled back (R5) |
| T-TRF-8 | Money conservation | Sum of all balances equals sum of top-ups after any sequence of transfers (R6) |

### Idempotency (R7)

| ID | Case | Expected |
|---|---|---|
| T-IDM-1 | Same key sent twice | One transfer; second call returns the same result; balances changed once |
| T-IDM-2 | Same key sent concurrently (10 requests) | Exactly one transfer created |
| T-IDM-3 | Different keys, same payload | Two separate transfers |
| T-IDM-4 | Missing `Idempotency-Key` header | 400 `VALIDATION_ERROR` |

### History (F6)

| ID | Case | Expected |
|---|---|---|
| T-HIS-1 | After top-up and transfers | Newest first; correct type, amount, counterparty |
| T-HIS-2 | Sender and recipient views | Each sees the transfer once, with the right direction |
| T-HIS-3 | User sees only their own transactions | Other users' entries never returned |

### Concurrency

| ID | Case | Expected |
|---|---|---|
| T-CON-1 | 100 parallel transfers of 10 from a wallet holding 500 | Exactly 50 succeed; rest `INSUFFICIENT_FUNDS`; balance never negative |
| T-CON-2 | Opposite transfers A→B and B→A in parallel (many) | No deadlock; total money unchanged |

### Manual frontend checklist

- [ ] Register → login → see balance 0
- [ ] Top up → balance and history update
- [ ] Transfer to a second account → both accounts correct
- [ ] Error messages shown for insufficient funds, unknown email, bad input
- [ ] Logout blocks access to the wallet page

## Exit criteria

- All automated tests pass on a clean checkout.
- T-CON-1 and T-CON-2 pass repeatedly (run 5 times).
- Manual checklist complete.
