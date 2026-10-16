package com.assessment.disputeportal.security;
import com.assessment.disputeportal.auth.cli.AccountAdministrationService;
import com.assessment.disputeportal.auth.service.AccountAdministrationTestConfiguration;
import com.assessment.disputeportal.security.activity.SessionActivityStore;
import com.assessment.disputeportal.auth.model.AccountRole;
import com.assessment.disputeportal.security.activity.JdbcSessionActivityStore;
import com.assessment.disputeportal.security.authentication.AccountAuthenticationProvider;
import com.assessment.disputeportal.auth.model.AccountIdentity;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.assertj.core.api.Assertions.*;
class AuthenticationConcurrencyTest extends MultiInstanceSupport {
    @Override Class<?>[] extraSources() { return new Class<?>[]{AccountAdministrationTestConfiguration.class,GatedActivity.class}; }
    @Test void logoutAndOldInFlightFrameworkSaveCannotResurrectAuthentication() throws Exception {
        create();String cookie=login(a,null);
        org.springframework.session.SessionRepository repository=a.getBean(org.springframework.session.jdbc.JdbcIndexedSessionRepository.class);
        org.springframework.session.Session stale=repository.findById(sessionId(cookie));
        var gate=a.getBean(Gate.class);gate.arm();
        try(var pool=Executors.newSingleThreadExecutor()) {
            var pending=pool.submit(()->identity(a,cookie));assertThat(gate.entered.await(5,TimeUnit.SECONDS)).isTrue();
            assertThat(send(b,"POST","/api/v1/auth/logout",cookie,token(b,cookie),null).statusCode()).isEqualTo(200);
            gate.release.countDown();assertThat(pending.get(10,TimeUnit.SECONDS)).isEqualTo(401);
        } finally { gate.release.countDown(); }
        stale.setAttribute("late-test-context", "untrusted");repository.save(stale);
        assertThat(identity(a,cookie)).isEqualTo(401);assertThat(identity(b,cookie)).isEqualTo(401);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auth_session_activity",Integer.class)).isZero();
    }
    @Test void concurrentIdentifierRotationDoesNotRestoreOldIdentifierOrMetadata() throws Exception {
        create();String old=login(a,null);var gate=a.getBean(Gate.class);gate.arm();String fresh;
        try(var pool=Executors.newSingleThreadExecutor()) {
            var pending=pool.submit(()->identity(a,old));assertThat(gate.entered.await(5,TimeUnit.SECONDS)).isTrue();
            fresh=login(b,old);gate.release.countDown();assertThat(pending.get(10,TimeUnit.SECONDS)).isEqualTo(401);
        } finally { gate.release.countDown(); }
        assertThat(identity(a,old)).isEqualTo(401);assertThat(identity(a,fresh)).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auth_session_activity",Integer.class)).isEqualTo(1);
    }
    @Test void administrativeChangeOnOtherInstanceBeforeActivationRejectsLogin() throws Exception {
        var account=create();var gate=a.getBean(Gate.class);gate.armActivation();
        var bootstrap=send(a,"GET","/api/v1/auth/csrf",null,null,null);var c=cookie(bootstrap);var t=json.readTree(bootstrap.body()).at("/data/csrfToken").asText();
        try(var pool=Executors.newSingleThreadExecutor()) {
            var pending=pool.submit(()->send(a,"POST","/api/v1/auth/login",c,t,json.writeValueAsString(Map.of("email","multi@example.test","password",PASSWORD))));
            assertThat(gate.awaitActivationEntered()).isTrue();
            b.getBean(AccountAdministrationService.class).setEnabled(account.id(),0,false,"isolated-test");
            gate.unblockActivation();assertThat(pending.get(10,TimeUnit.SECONDS).statusCode()).isEqualTo(401);
        } finally { gate.unblockActivation(); }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auth_session_activity",Integer.class)).isZero();
    }
    @Test void simultaneousEligibleRequestsRemainMonotonicAcrossInstances() throws Exception {
        create();String c=login(a,null);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var barrier=new CyclicBarrier(2);var one=pool.submit(()->{barrier.await();return identity(a,c);});var two=pool.submit(()->{barrier.await();return identity(b,c);});
            assertThat(one.get(10,TimeUnit.SECONDS)).isEqualTo(200);assertThat(two.get(10,TimeUnit.SECONDS)).isEqualTo(200);
        }
        assertThat(jdbc.queryForObject("SELECT last_authenticated_activity_at FROM auth_session_activity",Long.class)).isPositive();
    }
    @TestConfiguration(proxyBeanMethods=false) static class GatedActivity {
        @Bean @Primary Gate gate(JdbcSessionActivityStore delegate) { return new Gate(delegate); }
    }
    static final class Gate implements SessionActivityStore {
        final JdbcSessionActivityStore delegate;final AtomicBoolean armed=new AtomicBoolean();
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        final AtomicBoolean activationArmed=new AtomicBoolean();
        CountDownLatch activationEntered=new CountDownLatch(1),activationRelease=new CountDownLatch(1);
        Gate(JdbcSessionActivityStore delegate) { this.delegate=delegate; }
        void arm() { entered=new CountDownLatch(1);release=new CountDownLatch(1);armed.set(true); }
        void armActivation() { activationEntered=new CountDownLatch(1);activationRelease=new CountDownLatch(1);activationArmed.set(true); }
        boolean awaitActivationEntered() throws InterruptedException { return activationEntered.await(5,TimeUnit.SECONDS); }
        void unblockActivation() { activationRelease.countDown(); }
        public Optional<Snapshot> inspect(String id) { return delegate.inspect(id); }
        public boolean activate(String id,AccountIdentity account,long access) {
            if(activationArmed.compareAndSet(true,false)) { activationEntered.countDown();try { if(!activationRelease.await(10,TimeUnit.SECONDS))throw new IllegalStateException("Test barrier timeout"); } catch(InterruptedException e) { Thread.currentThread().interrupt();throw new IllegalStateException("Interrupted fixture"); } }
            return delegate.activate(id,account,access);
        }
        public boolean renew(String id,UUID account,long version,AccountRole role,long access) {
            if(armed.compareAndSet(true,false)) { entered.countDown();try { if(!release.await(10,TimeUnit.SECONDS))throw new IllegalStateException("Test barrier timeout"); } catch(InterruptedException e) { Thread.currentThread().interrupt();throw new IllegalStateException("Interrupted fixture"); } }
            return delegate.renew(id,account,version,role,access);
        }
    }
}
