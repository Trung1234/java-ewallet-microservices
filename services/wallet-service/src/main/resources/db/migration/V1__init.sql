-- Money is stored as BIGINT minor units, never floating point.

CREATE TABLE wallets (
    id          UUID PRIMARY KEY,
    user_id     VARCHAR(64)  NOT NULL UNIQUE,
    currency    VARCHAR(3)   NOT NULL,
    balance     BIGINT       NOT NULL DEFAULT 0,
    held        BIGINT       NOT NULL DEFAULT 0,  -- reserved by pending transfers / withdrawals
    version     BIGINT       NOT NULL DEFAULT 0,  -- optimistic locking
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    -- Last line of defence for INV-1, even if application code is wrong.
    CONSTRAINT wallets_balance_non_negative CHECK (balance >= 0),
    CONSTRAINT wallets_held_within_balance CHECK (held >= 0 AND held <= balance)
);

CREATE TABLE wallet_transactions (
    id             UUID PRIMARY KEY,
    wallet_id      UUID         NOT NULL REFERENCES wallets (id),
    type           VARCHAR(20)  NOT NULL,  -- TOPUP, WITHDRAW, HOLD, RELEASE, TRANSFER_OUT, TRANSFER_IN
    amount         BIGINT       NOT NULL CHECK (amount > 0),
    reference      VARCHAR(64)  NOT NULL,  -- bank operation id or transfer id
    balance_after  BIGINT       NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL,
    -- The same step of the same operation can never be applied twice.
    CONSTRAINT wallet_transactions_once UNIQUE (wallet_id, type, reference)
);
CREATE INDEX wallet_transactions_wallet_idx ON wallet_transactions (wallet_id, created_at DESC);

-- Top-ups and withdrawals that go through the (fake) bank.
CREATE TABLE bank_operations (
    id               UUID PRIMARY KEY,
    wallet_id        UUID          NOT NULL REFERENCES wallets (id),
    type             VARCHAR(20)   NOT NULL,  -- TOPUP, WITHDRAW
    amount           BIGINT        NOT NULL CHECK (amount > 0),
    idempotency_key  VARCHAR(100)  NOT NULL,
    status           VARCHAR(20)   NOT NULL,  -- PENDING, COMPLETED, FAILED
    failure_reason   VARCHAR(100),
    version          BIGINT        NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ   NOT NULL,
    updated_at       TIMESTAMPTZ   NOT NULL,
    CONSTRAINT bank_operations_idempotency UNIQUE (wallet_id, idempotency_key)
);

-- Transactional outbox (README §4.3) + columns for the polling relay.
CREATE TABLE outbox (
    id             UUID PRIMARY KEY,
    aggregatetype  VARCHAR(255) NOT NULL,
    aggregateid    VARCHAR(255) NOT NULL,
    type           VARCHAR(255) NOT NULL,
    payload        JSONB        NOT NULL,
    topic          VARCHAR(255) NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at   TIMESTAMPTZ
);
CREATE INDEX outbox_unpublished_idx ON outbox (created_at) WHERE published_at IS NULL;

-- Idempotent consumer: ids of events already handled.
CREATE TABLE processed_events (
    event_id      UUID PRIMARY KEY,
    processed_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
