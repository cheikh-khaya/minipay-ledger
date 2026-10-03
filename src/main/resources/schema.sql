-- Schema for minipay-ledger. Compatible with PostgreSQL (runtime) and H2 in PostgreSQL mode (tests).
-- Money is stored as NUMERIC(19,2): exact decimal, never floating point.

CREATE TABLE IF NOT EXISTS accounts (
    id          UUID PRIMARY KEY,
    owner_name  VARCHAR(100)   NOT NULL,
    currency    VARCHAR(3)     NOT NULL,
    balance     NUMERIC(19, 2) NOT NULL CHECK (balance >= 0),
    version     BIGINT         NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS transfers (
    id               UUID PRIMARY KEY,
    idempotency_key  VARCHAR(100)   NOT NULL UNIQUE,
    from_account_id  UUID           NOT NULL REFERENCES accounts (id),
    to_account_id    UUID           NOT NULL REFERENCES accounts (id),
    amount           NUMERIC(19, 2) NOT NULL CHECK (amount > 0),
    currency         VARCHAR(3)     NOT NULL,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS ledger_entries (
    id           UUID PRIMARY KEY,
    account_id   UUID           NOT NULL REFERENCES accounts (id),
    transfer_id  UUID           REFERENCES transfers (id),
    type         VARCHAR(10)    NOT NULL,
    amount       NUMERIC(19, 2) NOT NULL CHECK (amount > 0),
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_ledger_entries_account ON ledger_entries (account_id);
