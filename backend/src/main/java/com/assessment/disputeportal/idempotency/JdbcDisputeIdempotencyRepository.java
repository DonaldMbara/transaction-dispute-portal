package com.assessment.disputeportal.idempotency;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.sql.SQLException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JdbcDisputeIdempotencyRepository implements DisputeIdempotencyRepository {
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;

    @Override
    public Optional<IdempotencyResult> find(UUID userId, String operation, String key) {
        List<IdempotencyResult> rows = jdbc.query("""
                SELECT request_hash, response_body::text AS response_body
                FROM idempotency_records WHERE user_id = :userId AND operation = :operation AND idempotency_key = :key
                """, Map.of("userId", userId, "operation", operation, "key", key), (rs, n) ->
                new IdempotencyResult(rs.getString("request_hash").trim(), readResponse(rs.getString("response_body"))));
        return rows.stream().findFirst();
    }

    @Override
    public void save(UUID userId, String operation, String key, String hash, JsonNode responseBody) {
        jdbc.update("""
                INSERT INTO idempotency_records
                    (id, user_id, operation, idempotency_key, request_hash, response_status, response_body, created_at)
                VALUES (:id, :userId, :operation, :key, :hash, 201, CAST(:body AS jsonb), now())
                """, Map.of("id", UUID.randomUUID(), "userId", userId, "operation", operation,
                        "key", key, "hash", hash, "body", responseBody.toString()));
    }

    private JsonNode readResponse(String value) throws SQLException {
        try { return json.readTree(value); }
        catch (java.io.IOException failure) { throw new SQLException("Stored idempotency response is invalid.", failure); }
    }
}
