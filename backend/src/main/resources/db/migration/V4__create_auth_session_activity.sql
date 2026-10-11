CREATE TABLE auth_session_activity (
    session_primary_id CHAR(36) PRIMARY KEY,
    account_id UUID NOT NULL,
    auth_version BIGINT NOT NULL,
    last_authenticated_activity_at BIGINT NOT NULL,
    CONSTRAINT fk_auth_activity_session FOREIGN KEY (session_primary_id)
        REFERENCES SPRING_SESSION(PRIMARY_ID) ON DELETE CASCADE,
    CONSTRAINT fk_auth_activity_account FOREIGN KEY (account_id)
        REFERENCES app_users(id) ON DELETE CASCADE,
    CONSTRAINT ck_auth_activity_version CHECK (auth_version >= 0),
    CONSTRAINT ck_auth_activity_time CHECK (last_authenticated_activity_at >= 0)
);

CREATE INDEX idx_auth_activity_account ON auth_session_activity (account_id);
CREATE INDEX idx_auth_activity_time ON auth_session_activity (last_authenticated_activity_at);
