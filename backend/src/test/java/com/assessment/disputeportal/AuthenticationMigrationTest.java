package com.assessment.disputeportal;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class AuthenticationMigrationTest {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:17@sha256:2d2b8998d31037bf721cfdf764d76ba74171b4fab3431b7f72c27c56ddbdf9e3")
                    .asCompatibleSubstituteFor("postgres"));

    private String schema;

    @BeforeEach
    void isolatedSchema() throws SQLException {
        schema = "auth_migration_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             var statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + schema);
        }
    }

    private Flyway flyway(String target) {
        return Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .schemas(schema).defaultSchema(schema).locations("classpath:db/migration").target(target).load();
    }

    private Connection connection() throws SQLException {
        var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        connection.setSchema(schema);
        return connection;
    }

    private long count(String sql) throws SQLException {
        try (var connection = connection(); var statement = connection.createStatement(); var rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getLong(1);
        }
    }

    @Test
    void freshInstallationUsesThePinnedSessionSchemaAndCreatesNoAccounts() throws Exception {
        assertThat(flyway("latest").migrate().migrationsExecuted).isEqualTo(6);
        assertThat(flyway("latest").validateWithResult().validationSuccessful).isTrue();
        assertThat(count("SELECT count(*) FROM app_users")).isZero();
        assertThat(count("SELECT count(*) FROM spring_session")).isZero();
        assertThat(count("SELECT count(*) FROM auth_session_activity")).isZero();
        assertThat(count("SELECT count(*) FROM accounts")).isZero();
        try (var shipped = new ClassPathResource("org/springframework/session/jdbc/schema-postgresql.sql").getInputStream();
             var migration = new ClassPathResource("db/migration/V3__create_jdbc_sessions.sql").getInputStream()) {
            assertThat(migration.readAllBytes()).isEqualTo(shipped.readAllBytes());
        }
        assertColumns("spring_session", Map.of(
                "primary_id", "character:36:NO", "session_id", "character:36:NO",
                "creation_time", "bigint:NO", "last_access_time", "bigint:NO",
                "max_inactive_interval", "integer:NO", "expiry_time", "bigint:NO",
                "principal_name", "character varying:100:YES"));
        assertColumns("spring_session_attributes", Map.of(
                "session_primary_id", "character:36:NO", "attribute_name", "character varying:200:NO",
                "attribute_bytes", "bytea:NO"));
        assertColumns("auth_session_activity", Map.of(
                "session_primary_id", "character:36:NO", "account_id", "uuid:NO",
                "auth_version", "bigint:NO", "last_authenticated_activity_at", "bigint:NO"));
        assertThat(count("SELECT count(*) FROM pg_indexes WHERE schemaname = '" + schema + "' AND indexname IN "
                + "('spring_session_pk','spring_session_ix1','spring_session_ix2','spring_session_ix3',"
                + "'spring_session_attributes_pk','auth_session_activity_pkey','idx_auth_activity_account',"
                + "'idx_auth_activity_time','uq_app_users_normalized_email')")).isEqualTo(9);
        assertThat(count("SELECT count(*) FROM information_schema.columns WHERE table_schema = '" + schema
                + "' AND table_name = 'app_users' AND column_name = 'auth_version' AND data_type = 'bigint' "
                + "AND is_nullable = 'NO' AND column_default = '0'")).isEqualTo(1);
    }

    @Test
    void upgradeAndRestartPreserveV1AndUnexpiredRows() throws SQLException {
        flyway("1").migrate();
        var checksum = flyway("1").info().current().getChecksum();
        assertThat(checksum).isEqualTo(1011929294);
        try (var connection = connection(); var statement = connection.createStatement()) {
            statement.execute(user("00000000-0000-0000-0000-000000000001", "canonical@example.test"));
        }
        assertThat(flyway("latest").migrate().migrationsExecuted).isEqualTo(5);
        try (var connection = connection(); var statement = connection.createStatement()) {
            statement.execute(session("00000000-0000-0000-0000-000000000002"));
        }
        assertThat(count("SELECT auth_version FROM app_users")).isZero();
        assertThat(flyway("latest").migrate().migrationsExecuted).isZero();
        assertThat(flyway("latest").info().all()[0].getChecksum()).isEqualTo(checksum);
        assertThat(flyway("latest").validateWithResult().validationSuccessful).isTrue();
        assertThat(count("SELECT count(*) FROM app_users")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM spring_session WHERE expiry_time > extract(epoch FROM clock_timestamp()) * 1000")).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"UPPER@example.test", " canonical@example.test ", "\tcanonical@example.test\n"})
    void noncanonicalLegacyEmailBlocksUpgradeWithoutPartialChanges(String email) throws SQLException {
        flyway("1").migrate();
        insertLegacyUser("00000000-0000-0000-0000-000000000001", email);
        assertThatThrownBy(() -> flyway("latest").migrate()).isInstanceOf(FlywayException.class)
                .hasStackTraceContaining("noncanonical login emails");
        assertFailedPreflightIsAtomic();
        assertThat(count("SELECT count(*) FROM app_users")).isEqualTo(1);
    }

    @Test
    void normalizationCollisionBlocksUpgradeWithoutMergingUsers() throws SQLException {
        flyway("1").migrate();
        insertLegacyUser("00000000-0000-0000-0000-000000000001", "collision@example.test");
        insertLegacyUser("00000000-0000-0000-0000-000000000002", " COLLISION@example.test ");
        assertThatThrownBy(() -> flyway("latest").migrate()).isInstanceOf(FlywayException.class)
                .hasStackTraceContaining("normalized email collisions");
        assertFailedPreflightIsAtomic();
        assertThat(count("SELECT count(*) FROM app_users")).isEqualTo(2);
    }

    private void assertFailedPreflightIsAtomic() throws SQLException {
        assertThat(count("SELECT count(*) FROM information_schema.columns WHERE table_schema = '" + schema
                + "' AND table_name = 'app_users' AND column_name = 'auth_version'")).isZero();
        assertThat(count("SELECT count(*) FROM pg_tables WHERE schemaname = '" + schema
                + "' AND tablename IN ('spring_session','auth_session_activity')")).isZero();
        assertThat(count("SELECT count(*) FROM flyway_schema_history WHERE version IS NOT NULL")).isEqualTo(1);
        assertThat(flyway("1").validateWithResult().validationSuccessful).isTrue();
    }

    private void insertLegacyUser(String id, String email) throws SQLException {
        try (var connection = connection(); var statement = connection.prepareStatement(
                "INSERT INTO app_users(id,email,password_hash,role,created_at) VALUES (?::uuid,?,'isolated-fixture-hash','CUSTOMER',now())")) {
            statement.setString(1, id);
            statement.setString(2, email);
            statement.executeUpdate();
        }
    }

    @Test
    void infrastructureConstraintsAndCascadesHoldWithRollbackOnlyFixtures() throws SQLException {
        flyway("latest").migrate();
        String user = "00000000-0000-0000-0000-000000000001";
        String session = "00000000-0000-0000-0000-000000000002";
        String missing = "00000000-0000-0000-0000-000000000003";
        try (var connection = connection(); var statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            try {
                statement.execute(user(user, "fixture@example.test"));
                statement.execute(session(session));
                statement.execute("INSERT INTO spring_session_attributes VALUES ('" + session + "','fixture',decode('01','hex'))");
                statement.execute(activity(session, user, 0, 1));
                rejected(connection, "UPDATE app_users SET auth_version = -1", "23514");
                rejected(connection, "UPDATE app_users SET email = 'FIXTURE@example.test'", "23514");
                rejected(connection, "UPDATE app_users SET auth_version = NULL", "23502");
                rejected(connection, session(session), "23505");
                rejected(connection, "INSERT INTO spring_session_attributes VALUES ('" + session + "','fixture',decode('01','hex'))", "23505");
                rejected(connection, activity(session, user, 0, 1), "23505");
                rejected(connection, activity(missing, user, 0, 1), "23503");
                rejected(connection, "UPDATE auth_session_activity SET account_id = '" + missing + "'", "23503");
                rejected(connection, "UPDATE auth_session_activity SET auth_version = -1", "23514");
                rejected(connection, "UPDATE auth_session_activity SET last_authenticated_activity_at = -1", "23514");
                rejected(connection, "UPDATE auth_session_activity SET account_id = NULL", "23502");
                statement.execute("DELETE FROM spring_session WHERE primary_id = '" + session + "'");
                assertEmpty(statement, "spring_session_attributes");
                assertEmpty(statement, "auth_session_activity");
                statement.execute(session(session));
                statement.execute(activity(session, user, 0, 1));
                statement.execute("DELETE FROM app_users WHERE id = '" + user + "'");
                assertEmpty(statement, "auth_session_activity");
            } finally {
                connection.rollback();
            }
        }
        assertThat(count("SELECT count(*) FROM app_users")).isZero();
        assertThat(count("SELECT count(*) FROM spring_session")).isZero();
        assertThat(count("SELECT count(*) FROM spring_session_attributes")).isZero();
        assertThat(count("SELECT count(*) FROM auth_session_activity")).isZero();
    }

    private void assertColumns(String table, Map<String, String> expected) throws SQLException {
        Map<String, String> actual = new LinkedHashMap<>();
        try (var connection = connection(); var statement = connection.prepareStatement(
                "SELECT column_name, data_type, character_maximum_length, is_nullable FROM information_schema.columns "
                        + "WHERE table_schema = ? AND table_name = ?")) {
            statement.setString(1, schema);
            statement.setString(2, table);
            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    var length = rows.getObject(3);
                    actual.put(rows.getString(1), rows.getString(2) + (length == null ? "" : ":" + length) + ":" + rows.getString(4));
                }
            }
        }
        assertThat(actual).isEqualTo(expected);
    }

    private static void rejected(Connection connection, String sql, String state) throws SQLException {
        var savepoint = connection.setSavepoint();
        try (var statement = connection.createStatement()) {
            assertThatThrownBy(() -> statement.execute(sql)).isInstanceOfSatisfying(SQLException.class,
                    error -> assertThat(error.getSQLState()).isEqualTo(state));
        } finally {
            connection.rollback(savepoint);
            connection.releaseSavepoint(savepoint);
        }
    }

    private static void assertEmpty(java.sql.Statement statement, String table) throws SQLException {
        try (var rows = statement.executeQuery("SELECT count(*) FROM " + table)) {
            rows.next();
            assertThat(rows.getInt(1)).isZero();
        }
    }

    private static String user(String id, String email) {
        return "INSERT INTO app_users(id,email,password_hash,role,created_at) VALUES ('" + id + "','" + email
                + "','isolated-fixture-hash','CUSTOMER',now())";
    }

    private static String session(String id) {
        return "INSERT INTO spring_session VALUES ('" + id + "','" + id + "',"
                + "(extract(epoch FROM clock_timestamp()) * 1000)::bigint,"
                + "(extract(epoch FROM clock_timestamp()) * 1000)::bigint,1800,"
                + "(extract(epoch FROM clock_timestamp()) * 1000)::bigint + 1800000,NULL)";
    }

    private static String activity(String session, String account, long version, long time) {
        return "INSERT INTO auth_session_activity VALUES ('" + session + "','" + account + "'," + version + "," + time + ")";
    }
}
