package com.assessment.disputeportal.auth.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import org.slf4j.LoggerFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.assessment.disputeportal.auth.service.AccountAdministrationService;
import com.assessment.disputeportal.auth.validation.EmailNormalizer;
import com.assessment.disputeportal.auth.model.AccountIdentity;
import com.assessment.disputeportal.auth.model.AccountRole;
import com.assessment.disputeportal.auth.repository.AppUserRepository;
import com.assessment.disputeportal.auth.repository.AccountRepository;
import com.assessment.disputeportal.auth.repository.JpaAccountRepository;
import com.assessment.disputeportal.auth.mapper.AccountMapper;
import com.assessment.disputeportal.security.authentication.VersionedPasswordEncoder;
import com.assessment.disputeportal.common.exception.BusinessConflictException;
import com.assessment.disputeportal.common.exception.InvalidOperationException;
import com.assessment.disputeportal.common.exception.InvalidRequestException;
import com.assessment.disputeportal.common.exception.ResourceNotFoundException;

import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

@DataJpaTest(showSql = false, properties = {"spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.show-sql=false",
        "logging.level.org.hibernate.engine.jdbc.spi.SqlExceptionHelper=OFF"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ValidationAutoConfiguration.class, EmailNormalizer.class, VersionedPasswordEncoder.class,
    AccountMapper.class, JpaAccountRepository.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
class AccountAdministrationServiceTest {
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse(
            "postgres:17@sha256:2d2b8998d31037bf721cfdf764d76ba74171b4fab3431b7f72c27c56ddbdf9e3").asCompatibleSubstituteFor("postgres"));
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", postgres::getJdbcUrl);
        properties.add("spring.datasource.username", postgres::getUsername);
        properties.add("spring.datasource.password", postgres::getPassword);
    }
    @Autowired AppUserRepository accounts;
    @Autowired AccountRepository accountStore;
    @Autowired EmailNormalizer emails;
    @Autowired VersionedPasswordEncoder passwords;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JdbcTemplate jdbc;
    AccountAdministrationService service;
    static final String PASSWORD = "synthetic provisioning password";
    @BeforeEach void clean() {
        jdbc.update("DELETE FROM app_users");
        service = new AccountAdministrationService(accountStore, emails, passwords, transactions);
    }
    AccountIdentity create() { return service.create("fixture@example.test", "CUSTOMER", true, PASSWORD, "test-operator"); }
    long version(UUID id) { return jdbc.queryForObject("SELECT auth_version FROM app_users WHERE id=?", Long.class, id); }

    @Test void bothRolesExplicitStatusCanonicalEmailFreshIdentifiersAndHashArePersisted() {
        var customer = create();
        var agent = service.create(" AGENT@EXAMPLE.TEST ", "AGENT", false, PASSWORD, "test-operator");
        assertThat(customer.id()).isNotEqualTo(agent.id());
        assertThat(customer.authVersion()).isZero();
        assertThat(agent).isEqualTo(new AccountIdentity(agent.id(), "agent@example.test", AccountRole.AGENT, false, 0));
        String hash = jdbc.queryForObject("SELECT password_hash FROM app_users WHERE id=?", String.class, agent.id());
        assertThat(passwords.matches(PASSWORD, hash)).isTrue();
    }

    @Test void acceptedMaintenanceKeepsIdentityAndAdvancesVersionExactlyOnce() {
        UUID id = create().id();
        assertThat(service.setEnabled(id, 0, false, "test-operator").authVersion()).isEqualTo(1);
        assertThat(service.setEnabled(id, 1, true, "test-operator").authVersion()).isEqualTo(2);
        assertThat(service.setRole(id, 2, "AGENT", "test-operator").authVersion()).isEqualTo(3);
        assertThat(service.replacePassword(id, 3, "replacement synthetic password", "test-operator").authVersion()).isEqualTo(4);
        assertThat(version(id)).isEqualTo(4);
        String hash = jdbc.queryForObject("SELECT password_hash FROM app_users WHERE id=?", String.class, id);
        assertThat(passwords.matches("replacement synthetic password", hash)).isTrue();
        assertThat(passwords.matches(PASSWORD, hash)).isFalse();
    }

    @Test void duplicateInvalidMissingStaleAndNoopRequestsNeverIncrementVersion() {
        UUID id = create().id();
        assertThatThrownBy(() -> service.create(" FIXTURE@example.test ", "AGENT", false, PASSWORD, "test-operator")).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> service.create("invalid", "CUSTOMER", true, PASSWORD, "test-operator")).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> service.create("valid@example.test", "ADMIN", true, PASSWORD, "test-operator")).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> service.create("valid@example.test", "AGENT", true, "short", "test-operator")).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> service.setEnabled(UUID.randomUUID(), 0, false, "test-operator")).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.setEnabled(id, 1, false, "test-operator")).isInstanceOf(BusinessConflictException.class);
        assertThatThrownBy(() -> service.setEnabled(id, 0, true, "test-operator")).isInstanceOf(BusinessConflictException.class);
        assertThatThrownBy(() -> service.setRole(id, 0, "CUSTOMER", "test-operator")).isInstanceOf(BusinessConflictException.class);
        assertThatThrownBy(() -> service.setRole(id, 0, "ADMIN", "test-operator")).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> service.replacePassword(id, 0, "short", "test-operator")).isInstanceOf(InvalidRequestException.class);
        assertThat(version(id)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app_users", Integer.class)).isEqualTo(1);
    }

    @Test void concurrentSameVersionMutationsHaveOneWinnerAndOneConflict() throws Exception {
        UUID id = create().id();
        var barrier = new CyclicBarrier(2);
        try (var workers = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> disable = () -> {
                barrier.await(10, TimeUnit.SECONDS);
                try { service.setEnabled(id, 0, false, "test-operator"); return true; }
                catch (BusinessConflictException conflict) { return false; }
            };
            var first = workers.submit(disable);
            var second = workers.submit(disable);
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS))).containsExactlyInAnyOrder(true, false);
        }
        assertThat(version(id)).isEqualTo(1);
    }

    @Test void concurrentCanonicalDuplicateCreationCommitsOnlyOneAccount() throws Exception {
        var barrier = new CyclicBarrier(2);
        try (var workers = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> create = () -> {
                barrier.await(10, TimeUnit.SECONDS);
                try { service.create(" CONCURRENT@EXAMPLE.TEST ", "AGENT", false, PASSWORD, "test-operator"); return true; }
                catch (org.springframework.dao.DataIntegrityViolationException conflict) { return false; }
            };
            var first = workers.submit(create);
            var second = workers.submit(create);
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS))).containsExactlyInAnyOrder(true, false);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app_users", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT auth_version FROM app_users", Long.class)).isZero();
    }

    @Test void hashingHasNoTransactionOrRowLockAndConcurrentChangeIsRevalidated() throws Exception {
        UUID id = create().id();
        String originalHash = jdbc.queryForObject("SELECT password_hash FROM app_users WHERE id=?", String.class, id);
        var hashing = new CountDownLatch(1);
        var continueHashing = new CountDownLatch(1);
        PasswordEncoder gated = new PasswordEncoder() {
            public String encode(CharSequence raw) {
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                hashing.countDown();
                try { assertThat(continueHashing.await(15, TimeUnit.SECONDS)).isTrue(); }
                catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IllegalStateException(error); }
                return passwords.encode(raw);
            }
            public boolean matches(CharSequence raw, String encoded) { return passwords.matches(raw, encoded); }
        };
        var replacement = new AccountAdministrationService(accountStore, emails, gated, transactions);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var pending = workers.submit(() -> replacement.replacePassword(id, 0, "new synthetic password", "test-operator"));
            try {
                assertThat(hashing.await(10, TimeUnit.SECONDS)).isTrue();
                // A second instance commits while the first is hashing: no row lock is held by the first.
                var secondInstance = new AccountAdministrationService(accountStore, emails, passwords, transactions);
                assertThat(workers.submit(() -> secondInstance.setRole(id, 0, "AGENT", "test-operator")).get(5, TimeUnit.SECONDS).authVersion()).isEqualTo(1);
            } finally { continueHashing.countDown(); }
            assertThatThrownBy(() -> pending.get(15, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(BusinessConflictException.class);
        }
        assertThat(version(id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT password_hash FROM app_users WHERE id=?", String.class, id)).isEqualTo(originalHash);
    }

    @Test void databaseFailureRollsBackBothMutationAndVersionAndAuditFollowsBoundary() {
        UUID id = create().id();
        jdbc.execute("CREATE FUNCTION reject_account_change() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'isolated fault'; END $$");
        jdbc.execute("CREATE TRIGGER isolated_account_fault BEFORE UPDATE ON app_users FOR EACH ROW EXECUTE FUNCTION reject_account_change()");
        var observed = new AtomicBoolean();
        var logger = (Logger) LoggerFactory.getLogger("account.administration.audit");
        var capture = new AppenderBase<ILoggingEvent>() {
            protected void append(ILoggingEvent event) {
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                assertThat(version(id)).isZero();
                assertThat(event.getFormattedMessage()).contains("outcome=failed").doesNotContain(PASSWORD, "isolated fault");
                observed.set(true);
            }
        };
        capture.start(); logger.addAppender(capture);
        try { assertThatThrownBy(() -> service.setEnabled(id, 0, false, "test-operator")).isInstanceOf(RuntimeException.class); }
        finally {
            logger.detachAppender(capture); capture.stop();
            jdbc.execute("DROP TRIGGER isolated_account_fault ON app_users");
            jdbc.execute("DROP FUNCTION reject_account_change()");
        }
        assertThat(observed).isTrue();
        assertThat(jdbc.queryForObject("SELECT enabled FROM app_users WHERE id=?", Boolean.class, id)).isTrue();
        assertThat(version(id)).isZero();
    }

    @Test void successAuditIsAfterCommitAndContainsOnlySafeReferences() {
        List<String> events = new ArrayList<>();
        var logger = (Logger) LoggerFactory.getLogger("account.administration.audit");
        var capture = new AppenderBase<ILoggingEvent>() {
            protected void append(ILoggingEvent event) {
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                assertThat(jdbc.queryForObject("SELECT count(*) FROM app_users", Integer.class)).isEqualTo(1);
                events.add(event.getFormattedMessage());
            }
        };
        capture.start(); logger.addAppender(capture);
        try { service.create("fixture@example.test", "CUSTOMER", true, PASSWORD, "unsafe\noperator"); }
        finally { logger.detachAppender(capture); capture.stop(); }
        assertThat(events).singleElement().asString().contains("timestamp=", "operator=host-operator", "action=create", "outcome=committed", "correlation=")
                .doesNotContain(PASSWORD, "fixture@example.test", "pbkdf2", "unsafe", "\n");
    }

    @Test void ambientTransactionIsRefusedAndTrustedDeleteDoesNotReuseIdentity() {
        var transaction = new TransactionTemplate(transactions);
        assertThatThrownBy(() -> transaction.execute(status -> create())).isInstanceOf(InvalidOperationException.class);
        UUID former = create().id();
        jdbc.update("DELETE FROM app_users WHERE id=?", former);
        assertThat(accounts.identityById(former)).isEmpty();
        assertThat(create().id()).isNotEqualTo(former);
    }
}
