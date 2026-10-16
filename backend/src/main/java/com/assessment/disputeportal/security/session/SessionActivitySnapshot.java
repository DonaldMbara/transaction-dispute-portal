package com.assessment.disputeportal.security.session;

import java.util.UUID;

public record SessionActivitySnapshot(UUID accountId, long capturedVersion, long currentVersion,
        boolean enabled, String role, long lastActivity, long databaseTime) {
    public boolean unexpired() {
        return lastActivity > databaseTime - SessionActivityStore.IDLE_MILLIS;
    }
}