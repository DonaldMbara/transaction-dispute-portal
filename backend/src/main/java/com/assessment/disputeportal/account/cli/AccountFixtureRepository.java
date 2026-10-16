package com.assessment.disputeportal.account.cli;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AccountFixtureRepository {
    private static final String SELECT_COLUMNS = "id, customer_id, account_number, account_type, balance, is_primary, created_at";
    private final JdbcTemplate jdbc;

    public Optional<FixtureRow> findByIdForUpdate(UUID id) {
        List<FixtureRow> rows = jdbc.query("SELECT " + SELECT_COLUMNS + " FROM accounts WHERE id = ? FOR UPDATE",
                AccountFixtureRepository::map, id);
        return rows.stream().findFirst();
    }

    public Optional<FixtureRow> findById(UUID id) {
        List<FixtureRow> rows = jdbc.query("SELECT " + SELECT_COLUMNS + " FROM accounts WHERE id = ?",
                AccountFixtureRepository::map, id);
        return rows.stream().findFirst();
    }

    public long countAll() {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM accounts", Long.class);
        return count == null ? 0 : count;
    }

    public void insertIfAbsent(FixtureRow row) {
        jdbc.update("INSERT INTO accounts(id, customer_id, account_number, account_type, balance, is_primary, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING",
                row.id(), row.customerId(), row.accountNumber(), row.accountType(), row.balance(), row.primary(),
                java.sql.Timestamp.from(row.createdAt()));
    }

    private static FixtureRow map(ResultSet row, int index) throws SQLException {
        OffsetDateTime createdAt = row.getObject("created_at", OffsetDateTime.class);
        return new FixtureRow(row.getObject("id", UUID.class), row.getObject("customer_id", UUID.class),
                row.getString("account_number"), row.getString("account_type"), row.getBigDecimal("balance"),
                row.getBoolean("is_primary"), createdAt.toInstant());
    }

    public record FixtureRow(UUID id, UUID customerId, String accountNumber, String accountType,
            BigDecimal balance, boolean primary, Instant createdAt) {}
}
