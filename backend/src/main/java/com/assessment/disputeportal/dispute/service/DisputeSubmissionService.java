package com.assessment.disputeportal.dispute.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import com.assessment.disputeportal.common.api.ApiEnvelope;
import com.assessment.disputeportal.dispute.exception.DisputeApiException;
import com.assessment.disputeportal.dispute.model.Dispute;
import com.assessment.disputeportal.dispute.model.DisputeEvent;
import com.assessment.disputeportal.dispute.model.DisputeReason;
import com.assessment.disputeportal.dispute.model.DisputeStatus;
import com.assessment.disputeportal.dispute.model.dto.DisputeSubmissionResponse;
import com.assessment.disputeportal.dispute.repository.DisputeSubmissionRepository;
import com.assessment.disputeportal.idempotency.DisputeIdempotencyRepository;
import com.assessment.disputeportal.idempotency.IdempotencyResult;
import com.assessment.disputeportal.transaction.port.DisputeTransactionPort;
import com.assessment.disputeportal.transaction.port.SubmissionTransaction;
import com.assessment.disputeportal.transaction.model.TransactionStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class DisputeSubmissionService {
    private static final String OPERATION = "customer-dispute-submit";
    private final DisputeTransactionPort transactions;
    private final DisputeSubmissionRepository disputes;
    private final DisputeIdempotencyRepository idempotency;
    private final ObjectMapper json;
    private final JdbcTemplate jdbc;
    private final PlatformTransactionManager transactionManager;

    public DisputeSubmissionResult submit(UUID customerId, UUID transactionId, DisputeReason reason,
            String rawDescription, String key) {
        String description = normalize(rawDescription);
        String hash = fingerprint(transactionId, reason, description);
        Optional<IdempotencyResult> prior = idempotency.find(customerId, OPERATION, key);
        if (prior.isPresent()) return replay(prior.get(), hash);

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        try {
            JsonNode response = tx.execute(status -> create(customerId, transactionId, reason, description, key, hash));
            return new DisputeSubmissionResult(response);
        } catch (DataIntegrityViolationException failure) {
            if (!isExpectedUniqueConstraint(failure)) throw failure;
            // Read after rollback: PostgreSQL marks the failed transaction as aborted.
            Optional<IdempotencyResult> concurrent = idempotency.find(customerId, OPERATION, key);
            if (concurrent.isPresent()) return replay(concurrent.get(), hash);
            if (disputes.existsForTransaction(transactionId)) throw DisputeApiException.alreadyExists();
            throw failure;
        } catch (DataAccessException failure) {
            if (isWaitTimeout(failure)) throw DisputeApiException.unavailable();
            throw failure;
        }
    }

    private JsonNode create(UUID customerId, UUID transactionId, DisputeReason reason, String description,
            String key, String hash) {
        jdbc.execute("SET LOCAL lock_timeout = '5s'");
        jdbc.execute("SET LOCAL statement_timeout = '8s'");
        Optional<IdempotencyResult> afterLockWait = idempotency.find(customerId, OPERATION, key);
        if (afterLockWait.isPresent()) return replay(afterLockWait.get(), hash).envelope();

        SubmissionTransaction transaction = transactions.lockOwned(customerId, transactionId)
                .orElseThrow(() -> new com.assessment.disputeportal.common.exception.ResourceNotFoundException("Transaction", "id", transactionId));
        // Re-read once more after transaction-row locking to resolve same-transaction retries.
        Optional<IdempotencyResult> committedWhileWaiting = idempotency.find(customerId, OPERATION, key);
        if (committedWhileWaiting.isPresent()) return replay(committedWhileWaiting.get(), hash).envelope();
        if (transaction.status() != TransactionStatus.COMPLETED) throw DisputeApiException.ineligible();
        if (!"OUT".equals(transaction.direction())) throw DisputeApiException.ineligible();
        if (disputes.existsForTransaction(transactionId)) throw DisputeApiException.alreadyExists();

        Instant now = Instant.now();
        UUID disputeId = UUID.randomUUID();
        var dispute = new Dispute(disputeId, transactionId, reason, description, DisputeStatus.OPEN, now, now, 0);
        disputes.insert(dispute);
        disputes.appendCreated(DisputeEvent.created(UUID.randomUUID(), disputeId, customerId, now));
        var data = new DisputeSubmissionResponse(disputeId, transactionId, reason, description, DisputeStatus.OPEN, now);
        JsonNode response = json.valueToTree(ApiEnvelope.success(data));
        idempotency.save(customerId, OPERATION, key, hash, response);
        return response;
    }

    private static String normalize(String raw) {
        if (raw == null) throw DisputeApiException.invalid();
        String value = raw.strip();
        if (value.isEmpty() || value.length() > 2000) throw DisputeApiException.invalid();
        return value;
    }

    private static String fingerprint(UUID transactionId, DisputeReason reason, String description) {
        String canonical = "{\"transactionId\":\"" + transactionId + "\",\"reason\":\"" + reason.name()
                + "\",\"description\":" + quote(description) + "}";
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private static String quote(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> { if (c < 0x20) out.append(String.format("\\u%04x", (int) c)); else out.append(c); }
            }
        }
        return out.append('"').toString();
    }

    private DisputeSubmissionResult replay(IdempotencyResult prior, String hash) {
        if (!prior.hash().equals(hash)) throw DisputeApiException.keyReused();
        return new DisputeSubmissionResult(prior.responseBody());
    }

    private static boolean isExpectedUniqueConstraint(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof java.sql.SQLException sql && "23505".equals(sql.getSQLState())) {
                String message = sql.getMessage();
                return message != null && (message.contains("uq_idempotency_user_operation_key")
                        || message.contains("uq_disputes_transaction"));
            }
        }
        return false;
    }

    private static boolean isWaitTimeout(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof java.sql.SQLException sql
                    && ("55P03".equals(sql.getSQLState()) || "57014".equals(sql.getSQLState()))) return true;
        }
        return false;
    }
}
