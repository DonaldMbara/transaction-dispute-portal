-- Never silently normalize or merge an existing login identity.
DO $$
BEGIN
    IF EXISTS (
        SELECT lower(btrim(email, E' \t\n\r\f\013'))
        FROM app_users
        GROUP BY lower(btrim(email, E' \t\n\r\f\013'))
        HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION 'Authentication migration blocked: normalized email collisions. Review existing identities with an authorized operator before retrying.';
    END IF;
    IF EXISTS (
        SELECT 1 FROM app_users
        WHERE email <> lower(btrim(email, E' \t\n\r\f\013'))
    ) THEN
        RAISE EXCEPTION 'Authentication migration blocked: noncanonical login emails. Review and normalize existing identities with an authorized operator before retrying.';
    END IF;
END $$;

ALTER TABLE app_users
    ADD COLUMN auth_version BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_app_users_auth_version CHECK (auth_version >= 0),
    ADD CONSTRAINT ck_app_users_email_canonical
        CHECK (email = lower(btrim(email, E' \t\n\r\f\013')));

CREATE UNIQUE INDEX uq_app_users_normalized_email
    ON app_users (lower(btrim(email, E' \t\n\r\f\013')));
