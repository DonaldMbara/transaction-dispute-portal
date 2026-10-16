package com.assessment.disputeportal.security.session;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import com.assessment.disputeportal.auth.model.AccountRole;
import com.assessment.disputeportal.common.exception.InvalidRequestException;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public final class AccountSessionValidator {

    private final SessionActivityStore activity;

    public Optional<ValidatedSession> validate(Object principal, String sessionId, long accessTime) {
        if (!(principal instanceof SessionPrincipal identity) || sessionId == null || accessTime < 0) {
            return Optional.empty();
        }
        return activity.inspect(sessionId).flatMap(snapshot -> {
            if (!snapshot.accountId().equals(identity.accountId()) || !snapshot.enabled()
                    || snapshot.capturedVersion() < 0 || snapshot.currentVersion() < 0
                    || snapshot.capturedVersion() != snapshot.currentVersion() || !snapshot.unexpired()) {
                return Optional.empty();
            }
            final AccountRole role;
            try {
                role = AccountRole.parse(snapshot.role());
            } catch (InvalidRequestException unsupportedRole) {
                return Optional.empty();
            }
            return Optional.of(new ValidatedSession(sessionId, identity.accountId(), snapshot.currentVersion(), role, accessTime));
        });
    }
}
