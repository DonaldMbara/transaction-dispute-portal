CREATE TABLE accounts (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL REFERENCES app_users(id),
    account_number VARCHAR(32) NOT NULL UNIQUE
        CHECK (account_number ~ '^[0-9]{10}$'),
    account_type VARCHAR(32) NOT NULL
        CHECK (account_type IN ('MAIN', 'SAVINGS')),
    balance NUMERIC(19,2) NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX idx_accounts_primary
    ON accounts(customer_id) WHERE is_primary;

CREATE INDEX idx_accounts_customer
    ON accounts(customer_id);
