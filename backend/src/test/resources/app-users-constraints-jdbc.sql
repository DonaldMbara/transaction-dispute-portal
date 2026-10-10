DO $$
DECLARE
    actual_columns integer;
    history_count integer;
BEGIN
    SELECT count(*) INTO actual_columns FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 'app_users' AND is_nullable = 'NO'
      AND ((column_name = 'id' AND data_type = 'uuid')
        OR (column_name = 'email' AND data_type = 'character varying' AND character_maximum_length = 254)
        OR (column_name = 'password_hash' AND data_type = 'character varying' AND character_maximum_length = 255)
        OR (column_name = 'role' AND data_type = 'character varying' AND character_maximum_length = 16)
        OR (column_name = 'enabled' AND data_type = 'boolean' AND column_default = 'true')
        OR (column_name = 'created_at' AND data_type = 'timestamp with time zone'));
    IF actual_columns <> 6 THEN RAISE EXCEPTION 'Schema mismatch'; END IF;
    SELECT count(*) INTO history_count FROM flyway_schema_history
      WHERE version = '1' AND script = 'V1__create_app_users.sql' AND success AND checksum IS NOT NULL;
    IF history_count <> 1 THEN RAISE EXCEPTION 'Flyway V1 history mismatch'; END IF;

    INSERT INTO app_users (id,email,password_hash,role,created_at)
    VALUES ('00000000-0000-0000-0000-000000000001','constraint-check@example.invalid','test-only-hash','CUSTOMER',now());
    IF NOT (SELECT enabled FROM app_users WHERE id = '00000000-0000-0000-0000-000000000001') THEN
        RAISE EXCEPTION 'enabled default failed';
    END IF;
    INSERT INTO app_users (id,email,password_hash,role,enabled,created_at)
    VALUES ('00000000-0000-0000-0000-000000000002','agent-check@example.invalid','test-only-hash','AGENT',false,now());
    BEGIN
        INSERT INTO app_users (id,email,password_hash,role,created_at)
        VALUES ('00000000-0000-0000-0000-000000000003','constraint-check@example.invalid','test-only-hash','CUSTOMER',now());
        RAISE EXCEPTION 'Duplicate email accepted';
    EXCEPTION WHEN unique_violation THEN NULL; END;
    BEGIN
        INSERT INTO app_users (id,email,password_hash,role,created_at)
        VALUES ('00000000-0000-0000-0000-000000000001','pk-check@example.invalid','test-only-hash','CUSTOMER',now());
        RAISE EXCEPTION 'Duplicate ID accepted';
    EXCEPTION WHEN unique_violation THEN NULL; END;
    BEGIN
        INSERT INTO app_users (id,email,password_hash,role,created_at)
        VALUES ('00000000-0000-0000-0000-000000000003','role-check@example.invalid','test-only-hash','ADMIN',now());
        RAISE EXCEPTION 'Invalid role accepted';
    EXCEPTION WHEN check_violation THEN NULL; END;
    BEGIN
        INSERT INTO app_users (id,email,password_hash,role,created_at)
        VALUES ('00000000-0000-0000-0000-000000000003',NULL,'test-only-hash','CUSTOMER',now());
        RAISE EXCEPTION 'Null email accepted';
    EXCEPTION WHEN not_null_violation THEN NULL; END;
END $$;
