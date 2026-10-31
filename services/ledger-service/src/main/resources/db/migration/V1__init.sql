-- Double-entry ledger. Account names: 'wallet:<walletId>' and 'external:bank'.
-- Wallet balance = credits - debits on its account, so the ledger can be reconciled with wallet-service.

CREATE TABLE journals (
    id          UUID PRIMARY KEY,
    reference   VARCHAR(100) NOT NULL UNIQUE,  -- transfer id or bank operation id: one journal per business event
    type        VARCHAR(20)  NOT NULL,         -- TRANSFER, TOPUP, WITHDRAW
    created_at  TIMESTAMPTZ  NOT NULL
);

CREATE TABLE ledger_entries (
    id          UUID PRIMARY KEY,
    journal_id  UUID         NOT NULL REFERENCES journals (id),
    account     VARCHAR(100) NOT NULL,
    direction   VARCHAR(6)   NOT NULL CHECK (direction IN ('DEBIT', 'CREDIT')),
    amount      BIGINT       NOT NULL CHECK (amount > 0),
    created_at  TIMESTAMPTZ  NOT NULL
);
CREATE INDEX ledger_entries_account_idx ON ledger_entries (account);
CREATE INDEX ledger_entries_journal_idx ON ledger_entries (journal_id);

-- INV-4 (immutability): the ledger is append-only. Corrections are new journals, never edits.
CREATE FUNCTION forbid_ledger_change() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'ledger is append-only: % on % is not allowed', TG_OP, TG_TABLE_NAME;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER journals_append_only BEFORE UPDATE OR DELETE ON journals
    FOR EACH ROW EXECUTE FUNCTION forbid_ledger_change();
CREATE TRIGGER journals_no_truncate BEFORE TRUNCATE ON journals
    FOR EACH STATEMENT EXECUTE FUNCTION forbid_ledger_change();
CREATE TRIGGER ledger_entries_append_only BEFORE UPDATE OR DELETE ON ledger_entries
    FOR EACH ROW EXECUTE FUNCTION forbid_ledger_change();
CREATE TRIGGER ledger_entries_no_truncate BEFORE TRUNCATE ON ledger_entries
    FOR EACH STATEMENT EXECUTE FUNCTION forbid_ledger_change();

-- INV-4 (balance): checked at COMMIT, after all entries of the journal are inserted.
CREATE FUNCTION check_journal_balanced() RETURNS trigger AS $$
DECLARE
    difference BIGINT;
BEGIN
    SELECT COALESCE(SUM(CASE direction WHEN 'DEBIT' THEN amount ELSE -amount END), 0)
      INTO difference
      FROM ledger_entries
     WHERE journal_id = NEW.journal_id;
    IF difference <> 0 THEN
        RAISE EXCEPTION 'journal % is not balanced: debits - credits = %', NEW.journal_id, difference;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER ledger_entries_balanced AFTER INSERT ON ledger_entries
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION check_journal_balanced();

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
