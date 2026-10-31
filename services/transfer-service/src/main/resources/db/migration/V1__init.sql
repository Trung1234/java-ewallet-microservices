CREATE TABLE transfers (
    id               UUID PRIMARY KEY,
    idempotency_key  VARCHAR(100) NOT NULL,
    from_user_id     VARCHAR(64)  NOT NULL,
    from_wallet_id   UUID         NOT NULL,
    to_user_id       VARCHAR(64)  NOT NULL,
    to_wallet_id     UUID         NOT NULL,
    amount           BIGINT       NOT NULL CHECK (amount > 0),
    status           VARCHAR(20)  NOT NULL,  -- PENDING, HELD, COMPLETED, COMPENSATING, FAILED
    failure_reason   VARCHAR(100),
    version          BIGINT       NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL,
    -- INV-3: one transfer per (user, Idempotency-Key), even under concurrent requests.
    CONSTRAINT transfers_idempotency UNIQUE (from_user_id, idempotency_key),
    CONSTRAINT transfers_not_to_self CHECK (from_wallet_id <> to_wallet_id)
);
CREATE INDEX transfers_from_user_idx ON transfers (from_user_id, created_at DESC);

-- Local read model of wallet ownership, fed by WalletCreated events (no call to wallet-service).
CREATE TABLE wallet_directory (
    wallet_id   UUID PRIMARY KEY,
    user_id     VARCHAR(64) NOT NULL UNIQUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

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

CREATE TABLE processed_events (
    event_id      UUID PRIMARY KEY,
    processed_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
