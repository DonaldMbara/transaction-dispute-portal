package com.assessment.disputeportal.auth.repository;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.assessment.disputeportal.auth.repository.AccountLookup;
import com.assessment.disputeportal.auth.validation.EmailNormalizer;
import com.assessment.disputeportal.auth.model.AccountIdentity;
import com.assessment.disputeportal.auth.model.AccountRole;
import com.assessment.disputeportal.auth.model.AppUser;
import com.assessment.disputeportal.auth.repository.AppUserRepository;
import com.assessment.disputeportal.auth.repository.JpaAccountLookup;
import com.assessment.disputeportal.security.authentication.VersionedPasswordEncoder;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(showSql = false, properties = {"spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.show-sql=false"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ValidationAutoConfiguration.class, EmailNormalizer.class, JpaAccountLookup.class, VersionedPasswordEncoder.class})
@Testcontainers
class AccountPersistenceTest {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:17@sha256:2d2b8998d31037bf721cfdf764d76ba74171b4fab3431b7f72c27c56ddbdf9e3")
                    .asCompatibleSubstituteFor("postgres"));

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", postgres::getJdbcUrl);
        properties.add("spring.datasource.username", postgres::getUsername);
        properties.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired AppUserRepository repository;
    @Autowired AccountLookup lookup;
    @Autowired EmailNormalizer emails;
    @Autowired VersionedPasswordEncoder encoder;
    @Autowired EntityManager entities;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;

    private AppUser fixture(String email, AccountRole role, boolean enabled) {
        AppUser account = new AppUser();
        account.setId(UUID.randomUUID());
        account.setEmail(emails.normalize(email));
        account.setPasswordHash(encoder.encode("isolated fixture password"));
        account.setRole(role);
        account.setEnabled(enabled);
        account.setCreatedAt(Instant.parse("2026-10-10T10:00:00Z"));
        return account;
    }

    @Test
    void realSchemaValidatesAndBothRolesRoundTripWithoutHashInLookup() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app_users", Integer.class)).isZero();
        for (AccountRole role : AccountRole.values()) {
            AppUser original = fixture(role.name() + "@example.test", role, role == AccountRole.CUSTOMER);
            repository.saveAndFlush(original);
            entities.clear();
                AppUser loaded = repository.findById(original.getId()).orElseThrow();
                assertThat(loaded.getId()).isEqualTo(original.getId());
                assertThat(loaded.getEmail()).isEqualTo(original.getEmail());
                assertThat(loaded.getRole()).isEqualTo(role);
                assertThat(loaded.isEnabled()).isEqualTo(original.isEnabled());
                assertThat(loaded.getCreatedAt()).isEqualTo(original.getCreatedAt());
                assertThat(loaded.getAuthVersion()).isZero();
                assertThat(encoder.matches("isolated fixture password", loaded.getPasswordHash())).isTrue();
                assertThat(lookup.findById(original.getId())).contains(new AccountIdentity(original.getId(), original.getEmail(), role,
                    original.isEnabled(), 0));
                assertThat(lookup.findByEmail(" \t" + original.getEmail().toUpperCase(java.util.Locale.ROOT) + "\r\n"))
                    .isEqualTo(lookup.findById(original.getId()));
        }
        assertThat(lookup.findById(UUID.randomUUID())).isEmpty();
        assertThat(lookup.findByEmail("missing@example.test")).isEmpty();
    }

    @Test
    void normalizedDuplicateIdentitiesAreRejectedByPostgres() {
        repository.saveAndFlush(fixture("DUPLICATE@example.test", AccountRole.CUSTOMER, true));
        assertThatThrownBy(() -> repository.saveAndFlush(fixture(" duplicate@EXAMPLE.TEST ", AccountRole.AGENT, true)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void noncanonicalBypassIsRejectedByTheDatabase() {
        AppUser account = fixture("canonical@example.test", AccountRole.CUSTOMER, true);
        repository.saveAndFlush(account);
        assertThatThrownBy(() -> jdbc.update("UPDATE app_users SET email = ? WHERE id = ?", " CANONICAL@example.test ", account.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void lockedLookupHoldsTheRowLockForTheCallingTransactionAndLeavesNoFixtures() {
        var transaction = new TransactionTemplate(transactions);
        AppUser account = transaction.execute(status -> repository.saveAndFlush(fixture("locking@example.test", AccountRole.AGENT, true)));
        try {
            transaction.executeWithoutResult(status -> {
                assertThat(repository.findByIdForUpdate(account.getId())).isPresent();
                assertThatThrownBy(() -> {
                    try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                         var statement = connection.prepareStatement("SELECT id FROM app_users WHERE id = ? FOR UPDATE NOWAIT")) {
                        statement.setObject(1, account.getId());
                        statement.executeQuery().close();
                    }
                }).isInstanceOfSatisfying(SQLException.class, error -> assertThat(error.getSQLState()).isEqualTo("55P03"));
            });
        } finally {
            transaction.executeWithoutResult(status -> repository.deleteById(account.getId()));
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app_users", Integer.class)).isZero();
    }
}
