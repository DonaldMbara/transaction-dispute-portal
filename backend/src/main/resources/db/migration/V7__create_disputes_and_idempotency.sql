CREATE TABLE disputes (
    id UUID PRIMARY KEY,
    transaction_id UUID NOT NULL REFERENCES bank_transactions(id),
    reason VARCHAR(64) NOT NULL CHECK (reason IN (
        'UNRECOGNIZED_TRANSACTION', 'DUPLICATE_CHARGE', 'INCORRECT_AMOUNT',
        'GOODS_OR_SERVICES_NOT_RECEIVED', 'OTHER'
    )),
    description VARCHAR(2000) NOT NULL CHECK (length(btrim(description)) BETWEEN 1 AND 2000),
    status VARCHAR(16) NOT NULL CHECK (status IN ('OPEN', 'UNDER_REVIEW', 'ACCEPTED', 'REJECTED')),
    assigned_agent_id UUID REFERENCES app_users(id),
    assigned_at TIMESTAMPTZ,
    internal_resolution_notes VARCHAR(4000),
    customer_resolution_message VARCHAR(2000),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    resolved_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    CONSTRAINT uq_disputes_transaction UNIQUE (transaction_id),
    CONSTRAINT ck_disputes_lifecycle CHECK (
        (status = 'OPEN' AND assigned_agent_id IS NULL AND assigned_at IS NULL AND resolved_at IS NULL)
        OR (status = 'UNDER_REVIEW' AND assigned_agent_id IS NOT NULL AND assigned_at IS NOT NULL AND resolved_at IS NULL)
        OR (status IN ('ACCEPTED', 'REJECTED') AND assigned_agent_id IS NOT NULL AND assigned_at IS NOT NULL
            AND resolved_at IS NOT NULL AND internal_resolution_notes IS NOT NULL
            AND customer_resolution_message IS NOT NULL)
    )
);

CREATE INDEX idx_disputes_status_created ON disputes(status, created_at DESC, id DESC);
CREATE INDEX idx_disputes_agent_status_updated ON disputes(assigned_agent_id, status, updated_at DESC, id DESC);
CREATE INDEX idx_disputes_created ON disputes(created_at DESC, id DESC);

CREATE TABLE dispute_events (
    id UUID PRIMARY KEY,
    dispute_id UUID NOT NULL REFERENCES disputes(id),
    actor_id UUID NOT NULL REFERENCES app_users(id),
    dispute_version BIGINT NOT NULL CHECK (dispute_version >= 0),
    event_type VARCHAR(32) NOT NULL CHECK (event_type IN (
        'CREATED', 'CLAIMED', 'RELEASED', 'RESOLVED_ACCEPTED', 'RESOLVED_REJECTED'
    )),
    from_status VARCHAR(16) CHECK (from_status IN ('OPEN', 'UNDER_REVIEW', 'ACCEPTED', 'REJECTED')),
    to_status VARCHAR(16) NOT NULL CHECK (to_status IN ('OPEN', 'UNDER_REVIEW', 'ACCEPTED', 'REJECTED')),
    previous_agent_id UUID REFERENCES app_users(id),
    new_agent_id UUID REFERENCES app_users(id),
    internal_notes TEXT,
    customer_message TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_dispute_events_version UNIQUE (dispute_id, dispute_version),
    CONSTRAINT ck_dispute_events_created CHECK (
        (event_type <> 'CREATED') OR (dispute_version = 0 AND from_status IS NULL AND to_status = 'OPEN'
            AND previous_agent_id IS NULL AND new_agent_id IS NULL)
    )
);

CREATE TABLE idempotency_records (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_users(id),
    operation VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    response_status INTEGER NOT NULL CHECK (response_status = 201),
    response_body JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_idempotency_user_operation_key UNIQUE (user_id, operation, idempotency_key)
);
