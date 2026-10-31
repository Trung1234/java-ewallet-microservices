# Architecture — Class Diagrams

Class-level view of PayNest, derived from the code in `libs/` and `services/`. It describes what is implemented today. For intended behaviour see [product-spec.md](product-spec.md); for where the code differs from the README diagrams see [§13](#13-differences-from-the-readme-diagrams).

## Contents

1. [Module map](#1-module-map)
2. [How to read the diagrams](#2-how-to-read-the-diagrams)
3. [common-events](#3-common-events)
4. [outbox](#4-outbox)
5. [common-web](#5-common-web)
6. [user-service](#6-user-service)
7. [wallet-service](#7-wallet-service)
8. [transfer-service](#8-transfer-service)
9. [ledger-service](#9-ledger-service)
10. [notification-service](#10-notification-service)
11. [bank-adapter and api-gateway](#11-bank-adapter-and-api-gateway)
12. [How the services connect](#12-how-the-services-connect)
13. [Differences from the README diagrams](#13-differences-from-the-readme-diagrams)

---

## 1. Module map

Maven dependencies between modules. Services never depend on each other's code: they share only `libs/`.

```mermaid
flowchart TB
    subgraph services
        GW["api-gateway<br/>:8080"]
        US["user-service<br/>:8081, MongoDB"]
        WS["wallet-service<br/>:8082, PostgreSQL"]
        TS["transfer-service<br/>:8083, PostgreSQL"]
        LS["ledger-service<br/>:8084, PostgreSQL"]
        BA["bank-adapter<br/>:8085, in memory"]
        NS["notification-service<br/>:8086, MongoDB + Redis"]
    end
    subgraph libs
        CW["common-web"]
        OB["outbox"]
        CE["common-events"]
    end
    US --> CW
    WS --> CW & OB
    TS --> CW & OB
    LS --> CW & OB
    NS --> CW & CE
    OB --> CE
```

`api-gateway` and `bank-adapter` use no shared library. The gateway is reactive, and `common-web` is servlet-based, so the gateway keeps its own `JwtProperties` and `SecurityConfig`.

## 2. How to read the diagrams

| Notation | Meaning |
|---|---|
| `A --> B` | A holds B as an injected field |
| `A ..> B` | A creates, returns or reads B without holding it. Label `nested` means B is a record nested inside A |
| `A <.. B : someId` | B refers to A **by id only**; there is no object reference or JPA association |
| `A ..\|> B` | A implements interface B |
| `<<lib>>` | Class from a shared library; its members are in that library's section |
| `<<entity>>` / `<<document>>` | JPA entity (PostgreSQL) / Spring Data document (MongoDB) |
| `<<JpaRepository>>` / `<<MongoRepository>>` | Repository interface extending that Spring Data type; only its own query methods are listed |

To keep the diagrams readable, they leave out getters, constructors, loggers, private helper methods and the one-line `*Application` main classes. Injected dependencies appear as arrows, not as fields. Amounts are `long` integer minor units.

---

## 3. common-events

Package `com.paynest.events`. The wire format for everything on Kafka.

`Message` is a sealed interface: `EventCodec` discovers the payload types from its permitted subclasses and uses each simple class name as the wire name. All payloads are records nested in `WalletMessages`, `LedgerMessages` or `TransferMessages`, shown below as namespaces.

```mermaid
classDiagram
    direction LR

    class Message {
        <<sealed interface>>
    }
    class EventEnvelope {
        <<record>>
        +int CURRENT_VERSION$
        UUID eventId
        String eventType
        Instant occurredAt
        int version
        JsonNode payload
    }
    class EventCodec {
        -Map TYPES$
        -JsonMapper mapper
        +typeOf(Message payload) String$
        +encode(UUID eventId, Message payload) String
        +decode(String json) EventEnvelope
        +payloadOf(EventEnvelope envelope) Message
    }
    class Topics {
        +String WALLET_COMMANDS$
        +String WALLET_EVENTS$
        +String LEDGER_COMMANDS$
        +String LEDGER_EVENTS$
        +String TRANSFER_EVENTS$
        +List ALL$
    }

    namespace WalletMessages {
        class WalletCreated {
            <<event>>
            UUID walletId
            String userId
        }
        class HoldFunds {
            <<command>>
            UUID transferId
            UUID walletId
            long amount
        }
        class FundsHeld {
            <<event>>
            UUID transferId
            UUID walletId
            long amount
        }
        class FundsHoldFailed {
            <<event>>
            UUID transferId
            UUID walletId
            String reason
        }
        class CaptureFunds {
            <<command>>
            UUID transferId
            UUID fromWalletId
            UUID toWalletId
            long amount
        }
        class FundsCaptured {
            <<event>>
            UUID transferId
            UUID fromWalletId
            UUID toWalletId
            long amount
        }
        class ReleaseFunds {
            <<command>>
            UUID transferId
            UUID walletId
            long amount
        }
        class FundsReleased {
            <<event>>
            UUID transferId
            UUID walletId
            long amount
        }
        class WalletToppedUp {
            <<event>>
            UUID operationId
            UUID walletId
            long amount
        }
        class WalletWithdrawn {
            <<event>>
            UUID operationId
            UUID walletId
            long amount
        }
    }

    namespace LedgerMessages {
        class PostTransfer {
            <<command>>
            UUID transferId
            UUID fromWalletId
            UUID toWalletId
            long amount
        }
        class LedgerPosted {
            <<event>>
            UUID transferId
            UUID journalId
        }
        class LedgerPostingFailed {
            <<event>>
            UUID transferId
            String reason
        }
    }

    namespace TransferMessages {
        class TransferCompleted {
            <<event>>
            UUID transferId
            UUID fromWalletId
            String fromUserId
            UUID toWalletId
            String toUserId
            long amount
        }
        class TransferFailed {
            <<event>>
            UUID transferId
            UUID fromWalletId
            String fromUserId
            UUID toWalletId
            String toUserId
            long amount
            String reason
        }
    }

    EventCodec ..> EventEnvelope : encodes / decodes
    EventCodec ..> Message : typed payload
    EventEnvelope ..> Message : payload as JSON

    Message <|.. WalletCreated
    Message <|.. HoldFunds
    Message <|.. FundsHeld
    Message <|.. FundsHoldFailed
    Message <|.. CaptureFunds
    Message <|.. FundsCaptured
    Message <|.. ReleaseFunds
    Message <|.. FundsReleased
    Message <|.. WalletToppedUp
    Message <|.. WalletWithdrawn
    Message <|.. PostTransfer
    Message <|.. LedgerPosted
    Message <|.. LedgerPostingFailed
    Message <|.. TransferCompleted
    Message <|.. TransferFailed
```

---

## 4. outbox

Package `com.paynest.outbox`. Transactional outbox and idempotent inbox, used by the three PostgreSQL services. Each of them owns its `outbox` and `processed_events` tables in its own `V1__init.sql`.

- `OutboxWriter.append` and `Inbox.markProcessed` use `Propagation.MANDATORY`, so they fail if called outside the business transaction.
- `OutboxRelay` is on by default and turned off with `paynest.outbox.relay-enabled=false`, for when Debezium CDC publishes the outbox instead.
- `MessagingConfig` also sets the blocking retry policy for saga listeners: up to 6 retries with exponential backoff, then `<topic>.dlt`. Parse errors skip the retries.

```mermaid
classDiagram
    direction LR

    class OutboxWriter {
        <<Component>>
        -EntityManager entityManager
        +append(String aggregateType, Object key, String topic, Message payload) UUID
    }
    class OutboxRelay {
        <<Component>>
        -int batchSize
        -long sendTimeoutMs
        -boolean failing
        +publishPending() void
    }
    class Inbox {
        <<Component>>
        -JdbcTemplate jdbc
        +markProcessed(UUID eventId) boolean
    }
    class OutboxRepository {
        <<JpaRepository>>
        +lockNextBatch(int limit) List~OutboxEvent~
        +countByPublishedAtIsNull() long
        +findByTopicOrderByCreatedAt(String topic) List~OutboxEvent~
    }
    class OutboxEvent {
        <<entity>>
        -UUID id
        -String aggregateType
        -String aggregateId
        -String type
        -String payload
        -String topic
        -Instant createdAt
        -Instant publishedAt
        ~markPublished(Instant when) void
    }
    class MessagingConfig {
        <<Configuration>>
        ~eventCodec(JsonMapper jsonMapper) EventCodec
        ~paynestTopics(int partitions, int replicas) NewTopics
        ~kafkaErrorHandler(KafkaTemplate template) CommonErrorHandler
    }
    class EventCodec {
        <<lib>>
    }
    class Message {
        <<lib>>
    }
    class KafkaTemplate {
        <<Spring Kafka>>
    }

    OutboxWriter --> EventCodec
    OutboxWriter ..> OutboxEvent : persists
    OutboxWriter ..> Message : encodes
    OutboxRelay --> OutboxRepository
    OutboxRelay --> KafkaTemplate
    OutboxRelay ..> OutboxEvent : marks published
    OutboxRepository ..> OutboxEvent
    MessagingConfig ..> EventCodec : creates bean
```

---

## 5. common-web

Package `com.paynest.web`. Security and error handling for the servlet services. Services throw `ApiException` from controllers and services; to keep the diagrams clean, those arrows are not repeated in the service sections.

```mermaid
classDiagram
    direction LR

    class RuntimeException
    class ApiException {
        -HttpStatus status
        -String code
        +badRequest(String code, String message) ApiException$
        +forbidden(String code, String message) ApiException$
        +notFound(String code, String message) ApiException$
        +conflict(String code, String message) ApiException$
        +unprocessable(String code, String message) ApiException$
        +status() HttpStatus
        +code() String
    }
    class ApiError {
        <<record>>
        String code
        String message
    }
    class GlobalExceptionHandler {
        <<RestControllerAdvice>>
        ~handleApi(ApiException ex) ResponseEntity~ApiError~
        ~handleValidation(MethodArgumentNotValidException ex) ResponseEntity~ApiError~
        ~handleMissingHeader(MissingRequestHeaderException ex) ResponseEntity~ApiError~
        ~handleUnreadable(HttpMessageNotReadableException ex) ResponseEntity~ApiError~
        ~handleOther(Exception ex) ResponseEntity~ApiError~
    }
    class SecurityConfig {
        <<Configuration>>
        ~jwtDecoder(JwtProperties jwt) JwtDecoder
        ~securityFilterChain(HttpSecurity, SecurityProperties) SecurityFilterChain
    }
    class JwtProperties {
        <<ConfigurationProperties>>
        String secret
        String issuer
        Duration ttl
        +secretKey() SecretKey
    }
    class SecurityProperties {
        <<ConfigurationProperties>>
        List~String~ publicPaths
    }

    RuntimeException <|-- ApiException
    GlobalExceptionHandler ..> ApiException : handles
    GlobalExceptionHandler ..> ApiError : returns
    SecurityConfig ..> JwtProperties
    SecurityConfig ..> SecurityProperties
```

---

## 6. user-service

Package `com.paynest.user`. Registration, login and simulated KYC, stored in MongoDB. It is the only service that signs JWTs. It has no Kafka dependency.

```mermaid
classDiagram
    direction LR

    class AuthController {
        <<RestController>>
        +register(RegisterRequest request) UserResponse
        +login(LoginRequest request) TokenResponse
    }
    class UserController {
        <<RestController>>
        +me(Jwt jwt) UserResponse
        +submitKyc(Jwt jwt, KycRequest request) UserResponse
    }
    class RegisterRequest {
        <<record>>
        String email
        String password
        String fullName
    }
    class LoginRequest {
        <<record>>
        String email
        String password
    }
    class TokenResponse {
        <<record>>
        String accessToken
        String tokenType
        long expiresIn
    }
    class KycRequest {
        <<record>>
        String documentNumber
    }
    class UserResponse {
        <<record>>
        String id
        String email
        String fullName
        KycStatus kycStatus
        Instant createdAt
        ~from(User user) UserResponse$
    }
    class UserService {
        <<Service>>
        -Pattern ID_NUMBER$
        +register(String email, String password, String fullName) User
        +authenticate(String email, String password) User
        +get(String userId) User
        +submitKyc(String userId, String documentNumber) User
    }
    class TokenService {
        <<Service>>
        +issue(User user) String
        +ttlSeconds() long
    }
    class AuthConfig {
        <<Configuration>>
        ~passwordEncoder() PasswordEncoder
        ~jwtEncoder(JwtProperties jwt) JwtEncoder
    }
    class User {
        <<document>>
        -String id
        -String email
        -String passwordHash
        -String fullName
        -KycStatus kycStatus
        -Instant kycUpdatedAt
        -Instant createdAt
        +updateKyc(KycStatus status) void
    }
    class KycStatus {
        <<enumeration>>
        PENDING
        VERIFIED
        REJECTED
    }
    class UserRepository {
        <<MongoRepository>>
        +findByEmail(String email) Optional~User~
        +existsByEmail(String email) boolean
    }
    class JwtProperties {
        <<lib>>
    }
    class PasswordEncoder {
        <<Spring Security>>
    }
    class JwtEncoder {
        <<Spring Security>>
    }

    AuthController --> UserService
    AuthController --> TokenService
    UserController --> UserService
    AuthController ..> RegisterRequest : nested
    AuthController ..> LoginRequest : nested
    AuthController ..> TokenResponse : nested
    UserController ..> KycRequest : nested
    AuthController ..> UserResponse
    UserController ..> UserResponse
    UserResponse ..> User : from
    UserService --> UserRepository
    UserService --> PasswordEncoder
    TokenService --> JwtEncoder
    TokenService --> JwtProperties
    TokenService ..> User : signs claims of
    AuthConfig ..> PasswordEncoder : creates bean
    AuthConfig ..> JwtEncoder : creates bean
    UserRepository ..> User
    User --> KycStatus
```

---

## 7. wallet-service

Package `com.paynest.wallet`. Balances, holds, top-up and withdrawal, stored in PostgreSQL.

- **`WalletService`** handles user requests. A bank call never runs inside a DB transaction: it records the operation, calls the bank, then applies the result.
- **`WalletCommandService`** handles saga commands. Each command runs in one transaction with its reply event (outbox) and processed-event mark (inbox).
- **`Wallet`** invariants: `available = balance - held`. `wallets` has DB checks for `balance >= 0` and `0 <= held <= balance`.

```mermaid
classDiagram
    direction LR

    class WalletController {
        <<RestController>>
        +me(Jwt jwt) WalletResponse
        +topUp(Jwt, String idempotencyKey, AmountRequest) WalletResponse
        +withdraw(Jwt, String idempotencyKey, AmountRequest) WalletResponse
        +transactions(Jwt jwt) List~TransactionResponse~
    }
    class AmountRequest {
        <<record>>
        Long amount
    }
    class WalletResponse {
        <<record>>
        UUID id
        String userId
        String currency
        long balance
        long held
        long available
        Instant updatedAt
    }
    class TransactionResponse {
        <<record>>
        UUID id
        TransactionType type
        long amount
        String reference
        long balanceAfter
        Instant createdAt
    }
    class WalletService {
        <<Service>>
        ~String AGGREGATE$
        -int MAX_CONFLICT_RETRIES$
        -String currency
        +getOrCreate(String userId) Wallet
        +history(String userId) List~WalletTransaction~
        +topUp(String userId, String idempotencyKey, long amount) Wallet
        +withdraw(String userId, String idempotencyKey, long amount) Wallet
    }
    class WalletCommandListener {
        <<KafkaListener>>
        +onMessage(String message) void
    }
    class WalletCommandService {
        <<Service>>
        -String AGGREGATE$
        +handle(EventEnvelope envelope) void
    }
    class BankClient {
        <<Component>>
        -RestClient rest
        -int maxAttempts
        -Duration backoff
        +charge(UUID reference, long amount) BankResult
        +payout(UUID reference, long amount) BankResult
    }
    class BankRequest {
        <<record>>
        UUID reference
        long amount
    }
    class BankResult {
        <<record>>
        Outcome outcome
        String reason
        +ok() BankResult$
        +declined(String reason) BankResult$
        +unavailable(String reason) BankResult$
        +isOk() boolean
    }
    class Outcome["BankResult.Outcome"] {
        <<enumeration>>
        OK
        DECLINED
        UNAVAILABLE
    }
    class Wallet {
        <<entity>>
        -UUID id
        -String userId
        -String currency
        -long balance
        -long held
        -long version
        -Instant createdAt
        -Instant updatedAt
        +available() long
        +credit(long amount) void
        +hold(long amount) void
        +release(long amount) void
        +capture(long amount) void
    }
    class WalletTransaction {
        <<entity>>
        -UUID id
        -UUID walletId
        -TransactionType type
        -long amount
        -String reference
        -long balanceAfter
        -Instant createdAt
        +of(Wallet, TransactionType, long amount, Object reference) WalletTransaction$
    }
    class TransactionType {
        <<enumeration>>
        TOPUP
        WITHDRAW
        HOLD
        RELEASE
        TRANSFER_OUT
        TRANSFER_IN
    }
    class BankOperation {
        <<entity>>
        -UUID id
        -UUID walletId
        -Type type
        -long amount
        -String idempotencyKey
        -Status status
        -String failureReason
        -long version
        -Instant createdAt
        -Instant updatedAt
        +isPending() boolean
        +matches(Type type, long amount) boolean
        +complete() void
        +fail(String reason) void
    }
    class BankOperationType["BankOperation.Type"] {
        <<enumeration>>
        TOPUP
        WITHDRAW
    }
    class BankOperationStatus["BankOperation.Status"] {
        <<enumeration>>
        PENDING
        COMPLETED
        FAILED
    }
    class InsufficientFundsException
    class RuntimeException
    class WalletRepository {
        <<JpaRepository>>
        +findByUserId(String userId) Optional~Wallet~
    }
    class WalletTransactionRepository {
        <<JpaRepository>>
        +findTop50ByWalletIdOrderByCreatedAtDesc(UUID walletId) List~WalletTransaction~
        +existsByWalletIdAndTypeAndReference(UUID, TransactionType, String) boolean
    }
    class BankOperationRepository {
        <<JpaRepository>>
        +findByWalletIdAndIdempotencyKey(UUID walletId, String idempotencyKey) Optional~BankOperation~
    }
    class OutboxWriter {
        <<lib>>
    }
    class Inbox {
        <<lib>>
    }
    class EventCodec {
        <<lib>>
    }

    WalletController --> WalletService
    WalletController ..> AmountRequest : nested
    WalletController ..> WalletResponse : nested
    WalletController ..> TransactionResponse : nested

    WalletService --> WalletRepository
    WalletService --> WalletTransactionRepository
    WalletService --> BankOperationRepository
    WalletService --> OutboxWriter
    WalletService --> BankClient

    WalletCommandListener --> WalletCommandService
    WalletCommandListener --> EventCodec
    WalletCommandService --> WalletRepository
    WalletCommandService --> WalletTransactionRepository
    WalletCommandService --> OutboxWriter
    WalletCommandService --> Inbox
    WalletCommandService --> EventCodec

    BankClient ..> BankResult : returns
    BankClient ..> BankRequest : nested
    BankResult --> Outcome

    WalletRepository ..> Wallet
    WalletTransactionRepository ..> WalletTransaction
    BankOperationRepository ..> BankOperation

    Wallet "1" <.. "*" WalletTransaction : walletId
    Wallet "1" <.. "*" BankOperation : walletId
    WalletTransaction --> TransactionType
    BankOperation --> BankOperationType
    BankOperation --> BankOperationStatus
    Wallet ..> InsufficientFundsException : hold() throws
    RuntimeException <|-- InsufficientFundsException
```

---

## 8. transfer-service

Package `com.paynest.transfer`. Saga orchestrator for transfers, stored in PostgreSQL.

- **`TransferService`** stores the `PENDING` transfer and the first command (`HoldFunds`) in one transaction, then returns 202. `transfers` has a unique key on `(from_user_id, idempotency_key)`.
- **`TransferSaga`** moves the transfer one step for each reply and emits the next command through the outbox. It only acts when the transfer is in the expected state, so late or replayed replies do nothing.
- **`WalletDirectoryEntry`** is a local read model of wallet ownership, fed by `WalletCreated` events, so a transfer needs no call to wallet-service.

```mermaid
classDiagram
    direction LR

    class TransferController {
        <<RestController>>
        +create(Jwt jwt, String idempotencyKey, CreateTransferRequest request) ResponseEntity~TransferResponse~
        +get(Jwt jwt, UUID id) TransferResponse
        +list(Jwt jwt) List~TransferResponse~
    }
    class CreateTransferRequest {
        <<record>>
        UUID toWalletId
        Long amount
    }
    class TransferResponse {
        <<record>>
        UUID id
        TransferStatus status
        UUID fromWalletId
        UUID toWalletId
        long amount
        String failureReason
        Instant createdAt
        Instant updatedAt
    }
    class TransferService {
        <<Service>>
        +create(String userId, String idempotencyKey, UUID toWalletId, long amount) Transfer
        +get(String userId, UUID transferId) Transfer
        +list(String userId) List~Transfer~
    }
    class SagaReplyListener {
        <<KafkaListener>>
        +onMessage(String message) void
    }
    class TransferSaga {
        <<Service>>
        ~String AGGREGATE$
        +handle(EventEnvelope envelope) void
    }
    class Transfer {
        <<entity>>
        -UUID id
        -String idempotencyKey
        -String fromUserId
        -UUID fromWalletId
        -String toUserId
        -UUID toWalletId
        -long amount
        -TransferStatus status
        -String failureReason
        -long version
        -Instant createdAt
        -Instant updatedAt
        +transitionTo(TransferStatus next) void
        +transitionTo(TransferStatus next, String reason) void
        +isSameRequest(UUID toWalletId, long amount) boolean
        +involves(String userId) boolean
    }
    class TransferStatus {
        <<enumeration>>
        PENDING
        HELD
        COMPLETED
        COMPENSATING
        FAILED
        +canTransitionTo(TransferStatus next) boolean
        +isFinal() boolean
    }
    class WalletDirectoryEntry {
        <<entity>>
        -UUID walletId
        -String userId
        -Instant createdAt
    }
    class TransferRepository {
        <<JpaRepository>>
        +findByFromUserIdAndIdempotencyKey(String fromUserId, String idempotencyKey) Optional~Transfer~
        +findTop50ByFromUserIdOrToUserIdOrderByCreatedAtDesc(String fromUserId, String toUserId) List~Transfer~
    }
    class WalletDirectoryRepository {
        <<JpaRepository>>
        +findByUserId(String userId) Optional~WalletDirectoryEntry~
    }
    class OutboxWriter {
        <<lib>>
    }
    class Inbox {
        <<lib>>
    }
    class EventCodec {
        <<lib>>
    }

    TransferController --> TransferService
    TransferController ..> CreateTransferRequest : nested
    TransferController ..> TransferResponse : nested

    TransferService --> TransferRepository
    TransferService --> WalletDirectoryRepository
    TransferService --> OutboxWriter
    TransferService ..> TransferSaga : reads AGGREGATE

    SagaReplyListener --> TransferSaga
    SagaReplyListener --> EventCodec
    TransferSaga --> TransferRepository
    TransferSaga --> WalletDirectoryRepository
    TransferSaga --> OutboxWriter
    TransferSaga --> Inbox
    TransferSaga --> EventCodec

    TransferRepository ..> Transfer
    WalletDirectoryRepository ..> WalletDirectoryEntry
    Transfer --> TransferStatus
```

`TransferSaga.handle` reply table:

| Reply | Expected status | New status | Message emitted |
|---|---|---|---|
| `WalletCreated` | n/a | n/a | none; adds a `WalletDirectoryEntry` |
| `FundsHeld` | `PENDING` | `HELD` | `PostTransfer` |
| `FundsHoldFailed` | `PENDING` | `FAILED` | `TransferFailed` |
| `LedgerPosted` | `HELD` | `HELD` | `CaptureFunds` |
| `LedgerPostingFailed` | `HELD` | `COMPENSATING` | `ReleaseFunds` |
| `FundsCaptured` | `HELD` | `COMPLETED` | `TransferCompleted` |
| `FundsReleased` | `COMPENSATING` | `FAILED` | `TransferFailed` |

---

## 9. ledger-service

Package `com.paynest.ledger`. Double-entry ledger, stored in PostgreSQL.

- **`LedgerService.post`** writes one journal per reference and rejects unbalanced postings. `journals.reference` is unique.
- **Database enforcement:** triggers block `UPDATE`, `DELETE` and `TRUNCATE` on both tables, and a deferred trigger checks that each journal balances at commit.
- **`Accounts`** builds account names: `wallet:<walletId>` and `external:bank`.

```mermaid
classDiagram
    direction LR

    class LedgerController {
        <<RestController>>
        +balance(String account) AccountBalance
        +journal(String reference) JournalView
        +trialBalance() TrialBalance
    }
    class AccountBalance {
        <<record>>
        String account
        long debits
        long credits
        long balance
    }
    class EntryView {
        <<record>>
        String account
        Direction direction
        long amount
    }
    class JournalView {
        <<record>>
        UUID id
        String reference
        JournalType type
        Instant createdAt
        List~EntryView~ entries
    }
    class TrialBalance {
        <<record>>
        long totalDebits
        long totalCredits
        boolean balanced
    }
    class LedgerListener {
        <<KafkaListener>>
        +onMessage(String message) void
    }
    class LedgerMessageService {
        <<Service>>
        -String AGGREGATE$
        -long maxTransferAmount
        -double failureRate
        +handle(EventEnvelope envelope) void
    }
    class LedgerService {
        <<Service>>
        +post(String reference, JournalType type, List~Posting~ postings) Journal
    }
    class Journal {
        <<entity>>
        -UUID id
        -String reference
        -JournalType type
        -Instant createdAt
    }
    class LedgerEntry {
        <<entity>>
        -UUID id
        -UUID journalId
        -String account
        -Direction direction
        -long amount
        -Instant createdAt
    }
    class Posting {
        <<record>>
        String account
        Direction direction
        long amount
        +debit(String account, long amount) Posting$
        +credit(String account, long amount) Posting$
        ~signed() long
    }
    class Accounts {
        +String BANK$
        +wallet(UUID walletId) String$
    }
    class Direction {
        <<enumeration>>
        DEBIT
        CREDIT
    }
    class JournalType {
        <<enumeration>>
        TRANSFER
        TOPUP
        WITHDRAW
    }
    class JournalRepository {
        <<JpaRepository>>
        +findByReference(String reference) Optional~Journal~
    }
    class LedgerEntryRepository {
        <<JpaRepository>>
        +findByJournalId(UUID journalId) List~LedgerEntry~
        +sumByAccount(String account, Direction direction) long
        +sumAll(Direction direction) long
    }
    class OutboxWriter {
        <<lib>>
    }
    class Inbox {
        <<lib>>
    }
    class EventCodec {
        <<lib>>
    }

    LedgerController --> JournalRepository
    LedgerController --> LedgerEntryRepository
    LedgerController ..> AccountBalance : nested
    LedgerController ..> EntryView : nested
    LedgerController ..> JournalView : nested
    LedgerController ..> TrialBalance : nested

    LedgerListener --> LedgerMessageService
    LedgerListener --> EventCodec
    LedgerMessageService --> LedgerService
    LedgerMessageService --> JournalRepository
    LedgerMessageService --> OutboxWriter
    LedgerMessageService --> Inbox
    LedgerMessageService --> EventCodec
    LedgerMessageService ..> Posting : builds
    LedgerMessageService ..> Accounts : account names

    LedgerService --> JournalRepository
    LedgerService --> LedgerEntryRepository
    LedgerService ..> Journal : creates
    LedgerService ..> LedgerEntry : creates

    JournalRepository ..> Journal
    LedgerEntryRepository ..> LedgerEntry
    Journal "1" <.. "*" LedgerEntry : journalId
    LedgerEntry ..> Posting : built from
    Journal --> JournalType
    LedgerEntry --> Direction
    Posting --> Direction
```

---

## 10. notification-service

Package `com.paynest.notification`. Consumes transfer outcomes and delivers notifications, stored in MongoDB, with preferences cached in Redis.

- It uses `common-events` directly, not the `outbox` library: it publishes nothing and has its own `MessagingConfig`.
- **`TransferEventListener`** uses non-blocking retry topics (`.retry`), then `.dlt`, where `onDeadLetter` stores a `DeadLetter`.
- **Idempotency** works in two ways: `ProcessedEvent` skips handled events, and `Notification.idFor(event, user, channel)` gives each channel a fixed id, so a retry only re-sends the channels that failed.
- **`NotificationSender`** implementations run in `@Order`: in-app, email, push. Email and push only log, and email can simulate failures.

```mermaid
classDiagram
    direction LR

    class NotificationController {
        <<RestController>>
        +list(Jwt jwt, int page, int size) List~NotificationResponse~
        +unreadCount(Jwt jwt) UnreadCount
        +markRead(Jwt jwt, String id) NotificationResponse
        +getPreferences(Jwt jwt) PreferenceView
        +updatePreferences(Jwt, PreferenceRequest) PreferenceView
    }
    class PreferenceRequest {
        <<record>>
        Boolean emailEnabled
        Boolean pushEnabled
    }
    class UnreadCount {
        <<record>>
        long count
    }
    class NotificationResponse {
        <<record>>
        String id
        String title
        String body
        String eventType
        String transferId
        boolean read
        Instant createdAt
    }
    class TransferEventListener {
        <<KafkaListener>>
        +onEvent(String message) void
        +onDeadLetter(ConsumerRecord record) void
    }
    class MessagingConfig {
        <<Configuration>>
        ~eventCodec(JsonMapper jsonMapper) EventCodec
        ~transferEventsTopic(int partitions, int replicas) NewTopic
    }
    class NotificationHandler {
        <<Service>>
        +handle(EventEnvelope envelope) void
    }
    class PreferenceService {
        <<Service>>
        ~String CACHE$
        +get(String userId) PreferenceView
        +update(String userId, boolean email, boolean push) PreferenceView
    }
    class PreferenceView {
        <<record>>
        String userId
        boolean emailEnabled
        boolean pushEnabled
        ~defaults(String userId) PreferenceView$
        ~from(Preference preference) PreferenceView$
        +allows(Channel channel) boolean
    }
    class Templates {
        <<Component>>
        +transferSent(TransferCompleted event) Content
        +transferReceived(TransferCompleted event) Content
        +transferFailed(TransferFailed event) Content
    }
    class Content["Templates.Content"] {
        <<record>>
        String title
        String body
    }
    class NotificationSender {
        <<interface>>
        +channel() Channel
        +send(Notification notification) void
    }
    class InAppSender {
        <<Component>>
    }
    class EmailSender {
        <<Component>>
        -double failureRate
    }
    class PushSender {
        <<Component>>
    }
    class TemporarySendException
    class RuntimeException
    class Notification {
        <<document>>
        -String id
        -String userId
        -Channel channel
        -String eventType
        -String transferId
        -String title
        -String body
        -Status status
        -boolean read
        -Instant createdAt
        -Instant sentAt
        +idFor(UUID eventId, String userId, Channel channel) String$
        +markSent() void
        +markRead() void
    }
    class NotificationStatus["Notification.Status"] {
        <<enumeration>>
        PENDING
        SENT
    }
    class Channel {
        <<enumeration>>
        IN_APP
        EMAIL
        PUSH
    }
    class Preference {
        <<document>>
        String userId
        boolean emailEnabled
        boolean pushEnabled
        Instant updatedAt
    }
    class ProcessedEvent {
        <<document>>
        String eventId
        Instant processedAt
    }
    class DeadLetter {
        <<document>>
        String id
        String topic
        String key
        String payload
        String error
        Instant receivedAt
    }
    class NotificationRepository {
        <<MongoRepository>>
        +findByUserIdAndChannelOrderByCreatedAtDesc(String userId, Channel channel, Pageable page) List~Notification~
        +countByUserIdAndChannelAndReadFalse(String userId, Channel channel) long
        +findByTransferId(String transferId) List~Notification~
    }
    class PreferenceRepository {
        <<MongoRepository>>
    }
    class ProcessedEventRepository {
        <<MongoRepository>>
    }
    class DeadLetterRepository {
        <<MongoRepository>>
    }
    class EventCodec {
        <<lib>>
    }

    NotificationController --> NotificationRepository
    NotificationController --> PreferenceService
    NotificationController ..> PreferenceRequest : nested
    NotificationController ..> UnreadCount : nested
    NotificationController ..> NotificationResponse : nested

    TransferEventListener --> NotificationHandler
    TransferEventListener --> DeadLetterRepository
    TransferEventListener --> EventCodec
    MessagingConfig ..> EventCodec : creates bean

    NotificationHandler --> NotificationRepository
    NotificationHandler --> ProcessedEventRepository
    NotificationHandler --> PreferenceService
    NotificationHandler --> Templates
    NotificationHandler --> "*" NotificationSender : senders
    NotificationHandler --> EventCodec

    PreferenceService --> PreferenceRepository
    PreferenceService ..> PreferenceView : returns
    PreferenceView ..> Channel
    Templates ..> Content : returns

    NotificationSender <|.. InAppSender
    NotificationSender <|.. EmailSender
    NotificationSender <|.. PushSender
    NotificationSender ..> Notification : sends
    EmailSender ..> TemporarySendException : throws
    RuntimeException <|-- TemporarySendException

    NotificationRepository ..> Notification
    PreferenceRepository ..> Preference
    ProcessedEventRepository ..> ProcessedEvent
    DeadLetterRepository ..> DeadLetter
    Notification --> Channel
    Notification --> NotificationStatus
```

---

## 11. bank-adapter and api-gateway

**bank-adapter** (package `com.paynest.bank`) is a fake bank that fails on purpose. Its state is in memory and it is idempotent on `reference`. The gateway does not route to it, and only `BankClient` in wallet-service calls it.

```mermaid
classDiagram
    direction LR

    class BankController {
        <<RestController>>
        +charge(BankRequest request) BankReceipt
        +payout(BankRequest request) BankReceipt
        +chaos() Chaos
        +updateChaos(ChaosRequest request) Chaos
    }
    class BankRequest {
        <<record>>
        UUID reference
        long amount
    }
    class ChaosRequest {
        <<record>>
        double failureRate
        double timeoutRate
    }
    class FakeBank {
        <<Component>>
        -Map~UUID,BankReceipt~ processed
        -double failureRate
        -double timeoutRate
        -Duration slowResponse
        -long maxAmount
        +process(String operation, UUID reference, long amount) BankReceipt
        +chaos() Chaos
        +updateChaos(Chaos chaos) Chaos
    }
    class BankReceipt {
        <<record>>
        UUID reference
        String operation
        long amount
        String status
        Instant processedAt
    }
    class Chaos {
        <<record>>
        double failureRate
        double timeoutRate
    }

    BankController --> FakeBank
    BankController ..> BankRequest : nested
    BankController ..> ChaosRequest : nested
    FakeBank "1" o-- "*" BankReceipt : processed
    FakeBank ..> Chaos : nested
```

**api-gateway** (package `com.paynest.gateway`) is a reactive Spring Cloud Gateway. Its routes and rate-limit filter are configured in `application.yml`, so it has very little code.

```mermaid
classDiagram
    direction LR

    class SecurityConfig {
        <<Configuration>>
        ~jwtDecoder(JwtProperties jwt) ReactiveJwtDecoder
        ~securityWebFilterChain(ServerHttpSecurity http) SecurityWebFilterChain
    }
    class JwtProperties {
        <<ConfigurationProperties>>
        String secret
        String issuer
        +secretKey() SecretKey
    }
    class RateLimitConfig {
        <<Configuration>>
        ~principalOrIpKeyResolver() KeyResolver
    }

    SecurityConfig ..> JwtProperties
```

---

## 12. How the services connect

No service calls another's classes. The links between them are Kafka topics, one internal HTTP call, and the gateway routes.

**Kafka.** Topic names are in `Topics`. Wallet, transfer and ledger services publish through `OutboxWriter` and drop duplicates with `Inbox`. notification-service only consumes, and drops duplicates with `ProcessedEvent`.

| Topic | Message key | Message | Published by | Consumed by |
|---|---|---|---|---|
| `wallet.commands` | wallet id | `HoldFunds` | `TransferService` | `WalletCommandListener` |
| | | `CaptureFunds`, `ReleaseFunds` | `TransferSaga` | |
| `wallet.events` | wallet id | `WalletCreated`, `WalletToppedUp`, `WalletWithdrawn` | `WalletService` | `SagaReplyListener`, `LedgerListener` |
| | | `FundsHeld`, `FundsHoldFailed`, `FundsCaptured`, `FundsReleased` | `WalletCommandService` | |
| `ledger.commands` | transfer id | `PostTransfer` | `TransferSaga` | `LedgerListener` |
| `ledger.events` | transfer id | `LedgerPosted`, `LedgerPostingFailed` | `LedgerMessageService` | `SagaReplyListener` |
| `wallet.transfer.events` | transfer id | `TransferCompleted`, `TransferFailed` | `TransferSaga` | `TransferEventListener` |

Each listener reads every message on its topics and ignores the types it does not handle. For example, `LedgerMessageService` acts only on `PostTransfer`, `WalletToppedUp` and `WalletWithdrawn`.

**Internal HTTP.** `BankClient` (wallet-service) calls `POST /bank/charges` and `POST /bank/payouts` on `BankController` (bank-adapter), sending the `BankOperation` id as the idempotent `reference`.

**Gateway routes.** Every route except `/auth/**` needs a valid JWT. Each target service validates the JWT again itself.

| Path | Target controller |
|---|---|
| `/auth/**` | `AuthController` |
| `/users/**` | `UserController` |
| `/wallets/**` | `WalletController` |
| `/transfers/**` | `TransferController` |
| `/ledger/**` | `LedgerController` |
| `/notifications/**`, `/preferences/**` | `NotificationController` |

---

## 13. Differences from the README diagrams

The README §1 overview differs from the code in these places. The diagrams in this document follow the code.

| Topic | README §1 | Code |
|---|---|---|
| bank-adapter | Consumes from Kafka | HTTP only: `BankClient` calls `BankController`. The module has no Kafka dependency |
| Redis | Used by wallet and transfer services | Used by `api-gateway` (rate limiting) and `notification-service` (preference cache) |
| Gateway routes | User, wallet and transfer services | Also routes `/ledger/**`, `/notifications/**` and `/preferences/**` |
