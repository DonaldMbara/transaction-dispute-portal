-- Foundation's explicit schema exception. Authentication owns the later entity.
-- Email normalization and BCrypt password handling belong to authentication.
CREATE TABLE app_users (
    id UUID PRIMARY KEY,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(16) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_app_users_email UNIQUE (email),
    CONSTRAINT ck_app_users_role CHECK (role IN ('CUSTOMER', 'AGENT'))
);
