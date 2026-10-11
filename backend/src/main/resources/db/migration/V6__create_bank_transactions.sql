CREATE TABLE bank_transactions (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES accounts(id),
    reference VARCHAR(80) NOT NULL UNIQUE,
    merchant VARCHAR(160) NOT NULL,
    amount NUMERIC(19,2) NOT NULL CHECK (amount > 0),
    direction VARCHAR(8) NOT NULL CHECK (direction IN ('IN', 'OUT')),
    currency CHAR(3) NOT NULL DEFAULT 'ZAR' CHECK (currency = 'ZAR'),
    status VARCHAR(16) NOT NULL CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED')),
    occurred_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_transactions_account_time
    ON bank_transactions(account_id, occurred_at DESC, id DESC);

CREATE INDEX idx_transactions_time
    ON bank_transactions(occurred_at DESC, id DESC);
