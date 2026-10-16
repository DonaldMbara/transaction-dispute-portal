package com.assessment.disputeportal.transaction.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.assessment.disputeportal.transaction.model.BankTransaction;
import com.assessment.disputeportal.transaction.model.TransactionDirection;
import com.assessment.disputeportal.transaction.model.projection.TransactionReadRow;
import com.assessment.disputeportal.transaction.model.query.TransactionQuery;
import com.assessment.disputeportal.transaction.model.TransactionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JdbcTransactionReadRepository implements TransactionReadRepository {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Africa/Johannesburg");
    private static final String FROM_JOIN = " FROM bank_transactions t JOIN accounts a ON a.id = t.account_id LEFT JOIN disputes d ON d.transaction_id = t.id ";
    private static final String OWNERSHIP = " a.customer_id = :customerId ";
    private final NamedParameterJdbcTemplate jdbc;

    private static final RowMapper<TransactionReadRow> ROW_MAPPER = (rs, rowNum) -> mapRow(rs);

    @Override
    public boolean ownsAccount(UUID customerId, UUID accountId) {
        Boolean found = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM accounts WHERE id = :accountId AND customer_id = :customerId)",
                Map.of("accountId", accountId, "customerId", customerId), Boolean.class);
        return Boolean.TRUE.equals(found);
    }

    @Override
    public long count(UUID customerId, TransactionQuery query) {
        var sql = new StringBuilder("SELECT count(*)").append(FROM_JOIN).append("WHERE").append(OWNERSHIP);
        var parameters = new MapSqlParameterSource("customerId", customerId);
        appendFilters(sql, parameters, query);
        Long count = jdbc.queryForObject(sql.toString(), parameters, Long.class);
        return count == null ? 0 : count;
    }

    @Override
    public List<TransactionReadRow> list(UUID customerId, TransactionQuery query) {
        var sql = new StringBuilder("SELECT t.id, t.account_id, t.reference, t.merchant, t.amount, t.currency, ")
                .append("t.direction, t.status, t.occurred_at, a.account_number, d.id AS dispute_id")
                .append(FROM_JOIN).append("WHERE").append(OWNERSHIP);
        var parameters = new MapSqlParameterSource("customerId", customerId);
        appendFilters(sql, parameters, query);
        String order = query.sortBy().column();
        String direction = query.sortDirection().name();
        sql.append(" ORDER BY t.").append(order).append(' ').append(direction)
                .append(", t.id ").append(direction).append(" LIMIT :limit OFFSET :offset");
        parameters.addValue("limit", query.size()).addValue("offset", query.offset());
        return jdbc.query(sql.toString(), parameters, ROW_MAPPER);
    }

    @Override
    public Optional<TransactionReadRow> findOwned(UUID customerId, UUID transactionId) {
        List<TransactionReadRow> rows = jdbc.query("SELECT t.id, t.account_id, t.reference, t.merchant, t.amount, t.currency, "
                        + "t.direction, t.status, t.occurred_at, a.account_number, d.id AS dispute_id" + FROM_JOIN
                        + "WHERE a.customer_id = :customerId AND t.id = :transactionId",
                Map.of("customerId", customerId, "transactionId", transactionId), ROW_MAPPER);
        return rows.stream().findFirst();
    }

    private static void appendFilters(StringBuilder sql, MapSqlParameterSource parameters, TransactionQuery query) {
        if (query.accountId() != null) {
            sql.append(" AND t.account_id = :accountId AND a.customer_id = :accountOwner");
            parameters.addValue("accountId", query.accountId()).addValue("accountOwner", parameters.getValue("customerId"));
        }
        if (query.search() != null && !query.search().isEmpty()) {
            sql.append(" AND (lower(t.merchant) LIKE :search ESCAPE '\\' OR lower(t.reference) LIKE :search ESCAPE '\\')");
            parameters.addValue("search", "%" + escapeLike(query.search().toLowerCase(java.util.Locale.ROOT)) + "%");
        }
        if (query.direction() != null) {
            sql.append(" AND t.direction = :direction"); parameters.addValue("direction", query.direction().name());
        }
        if (query.status() != null) {
            sql.append(" AND t.status = :status"); parameters.addValue("status", query.status().name());
        }
        if (query.fromDate() != null) {
            sql.append(" AND t.occurred_at >= :fromInclusive");
            parameters.addValue("fromInclusive", timestamp(query.fromDate()));
        }
        if (query.toDate() != null) {
            sql.append(" AND t.occurred_at < :toExclusive");
            parameters.addValue("toExclusive", timestamp(query.toDate().plusDays(1)));
        }
        if (query.minAmount() != null) {
            sql.append(" AND t.amount >= :minAmount"); parameters.addValue("minAmount", query.minAmount());
        }
        if (query.maxAmount() != null) {
            sql.append(" AND t.amount <= :maxAmount"); parameters.addValue("maxAmount", query.maxAmount());
        }
    }

    private static OffsetDateTime timestamp(LocalDate date) {
        return date.atStartOfDay(BUSINESS_ZONE).toOffsetDateTime();
    }

    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static TransactionReadRow mapRow(ResultSet rs) throws SQLException {
        var transaction = new BankTransaction(rs.getObject("id", UUID.class), rs.getObject("account_id", UUID.class),
                rs.getString("reference"), rs.getString("merchant"), rs.getBigDecimal("amount"),
                rs.getString("currency").trim(), TransactionDirection.valueOf(rs.getString("direction")),
                TransactionStatus.valueOf(rs.getString("status")), rs.getTimestamp("occurred_at").toInstant());
        return new TransactionReadRow(transaction, rs.getString("account_number"), rs.getObject("dispute_id", UUID.class));
    }
}
