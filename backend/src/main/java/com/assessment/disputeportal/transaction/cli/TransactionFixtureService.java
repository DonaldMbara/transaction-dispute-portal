package com.assessment.disputeportal.transaction.cli;

import com.assessment.disputeportal.account.cli.AccountFixturePort;
import com.assessment.disputeportal.transaction.cli.TransactionFixtureRepository.FixtureRow;
import com.assessment.disputeportal.transaction.model.TransactionDirection;
import com.assessment.disputeportal.transaction.model.TransactionStatus;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionFixtureService {
  private static final List<FixtureDefinition> DEFINITIONS =
      List.of(
          new FixtureDefinition(
              "MAIN-001",
              "Grocery Market",
              "1250.00",
              TransactionDirection.OUT,
              TransactionStatus.COMPLETED,
              "2025-03-10T08:15:00Z"),
          new FixtureDefinition(
              "MAIN-002",
              "Salary Payment",
              "24500.00",
              TransactionDirection.IN,
              TransactionStatus.COMPLETED,
              "2025-03-15T06:00:00Z"),
          new FixtureDefinition(
              "MAIN-003",
              "Coffee House",
              "89.99",
              TransactionDirection.OUT,
              TransactionStatus.PENDING,
              "2025-03-18T10:30:00Z"),
          // Versioned additions keep any previously provisioned fixture rows immutable.
          new FixtureDefinition(
              "MAIN-2026-001",
              "Acme Consulting Salary",
              "31850.00",
              TransactionDirection.IN,
              TransactionStatus.COMPLETED,
              "2026-09-25T06:00:00Z"),
          new FixtureDefinition(
              "MAIN-2026-002",
              "Checkers Hyper",
              "642.75",
              TransactionDirection.OUT,
              TransactionStatus.COMPLETED,
              "2026-10-10T10:42:00Z"),
          new FixtureDefinition(
              "MAIN-2026-003",
              "Engen 1-Stop",
              "850.00",
              TransactionDirection.OUT,
              TransactionStatus.COMPLETED,
              "2026-10-09T15:18:00Z"),
          new FixtureDefinition(
              "MAIN-2026-004",
              "Uber Eats",
              "234.50",
              TransactionDirection.OUT,
              TransactionStatus.COMPLETED,
              "2026-10-08T18:36:00Z"),
          new FixtureDefinition(
              "MAIN-2026-005",
              "Netflix.com",
              "199.00",
              TransactionDirection.OUT,
              TransactionStatus.COMPLETED,
              "2026-10-07T00:05:00Z"),
          new FixtureDefinition(
              "MAIN-2026-006",
              "Woolworths Food",
              "417.38",
              TransactionDirection.OUT,
              TransactionStatus.COMPLETED,
              "2026-10-05T12:14:00Z"),
          new FixtureDefinition(
              "MAIN-2026-007",
              "Vodacom Data Bundle",
              "249.00",
              TransactionDirection.OUT,
              TransactionStatus.COMPLETED,
              "2026-10-03T08:22:00Z"),
          new FixtureDefinition(
              "MAIN-2026-008",
              "Cape Union Mart",
              "799.95",
              TransactionDirection.OUT,
              TransactionStatus.COMPLETED,
              "2026-09-30T13:47:00Z"),
          new FixtureDefinition(
              "MAIN-2026-009",
              "Mr D Food",
              "186.00",
              TransactionDirection.OUT,
              TransactionStatus.FAILED,
              "2026-09-29T19:12:00Z"),
          new FixtureDefinition(
              "SAVINGS-001",
              "Monthly Transfer",
              "1000.00",
              TransactionDirection.IN,
              TransactionStatus.COMPLETED,
              "2025-03-11T09:00:00Z"),
          new FixtureDefinition(
              "SAVINGS-002",
              "Interest Credit",
              "125.50",
              TransactionDirection.IN,
              TransactionStatus.COMPLETED,
              "2025-03-16T04:00:00Z"),
          new FixtureDefinition(
              "SAVINGS-003",
              "Scheduled Transfer",
              "300.00",
              TransactionDirection.OUT,
              TransactionStatus.FAILED,
              "2025-03-19T12:00:00Z"),
          new FixtureDefinition(
              "SAVINGS-2026-001",
              "Monthly Savings Contribution",
              "3500.00",
              TransactionDirection.IN,
              TransactionStatus.COMPLETED,
              "2026-10-01T07:15:00Z"),
          new FixtureDefinition(
              "SAVINGS-2026-002",
              "Savings Account Interest",
              "42.16",
              TransactionDirection.IN,
              TransactionStatus.COMPLETED,
              "2026-09-30T04:00:00Z"),
          new FixtureDefinition(
              "SAVINGS-2026-003",
              "Transfer to Main Account",
              "1200.00",
              TransactionDirection.OUT,
              TransactionStatus.COMPLETED,
              "2026-09-28T14:20:00Z"),
          new FixtureDefinition(
              "SAVINGS-2026-004",
              "Monthly Savings Contribution",
              "3500.00",
              TransactionDirection.IN,
              TransactionStatus.COMPLETED,
              "2026-09-01T07:15:00Z"),
          new FixtureDefinition(
              "SAVINGS-2026-005",
              "Emergency Fund Top-up",
              "500.00",
              TransactionDirection.IN,
              TransactionStatus.COMPLETED,
              "2026-08-20T16:35:00Z"),
          new FixtureDefinition(
              "SAVINGS-2026-006",
              "Savings Account Interest",
              "40.89",
              TransactionDirection.IN,
              TransactionStatus.COMPLETED,
              "2026-08-01T04:00:00Z"),
          new FixtureDefinition(
              "SAVINGS-2026-007",
              "Monthly Savings Contribution",
              "3500.00",
              TransactionDirection.IN,
              TransactionStatus.COMPLETED,
              "2026-08-01T07:15:00Z"),
          new FixtureDefinition(
              "SAVINGS-2026-008",
              "Transfer to Main Account",
              "800.00",
              TransactionDirection.OUT,
              TransactionStatus.COMPLETED,
              "2026-07-15T11:10:00Z"),
          new FixtureDefinition(
              "SAVINGS-2026-009",
              "Savings Account Interest",
              "38.21",
              TransactionDirection.IN,
              TransactionStatus.COMPLETED,
              "2026-06-30T04:00:00Z"));

  private final AccountFixturePort accounts;
  private final TransactionFixtureRepository transactions;

  @Transactional
  public int ensureCustomerFixtures(String customerEmail) {
    Map<String, UUID> accountIds = accounts.getFixtureAccountIds(customerEmail);
    List<FixtureRow> expected = new ArrayList<>();
    for (FixtureDefinition definition : DEFINITIONS) {
      String type = definition.key().startsWith("MAIN-") ? "MAIN" : "SAVINGS";
      UUID accountId = accountIds.get(type);
      if (accountId == null)
        throw new IllegalStateException("Both fixture account types are required.");
      UUID id = stableId(accountId, definition.key());
      expected.add(
          new FixtureRow(
              id,
              accountId,
              "TXN-" + id.toString().toUpperCase(java.util.Locale.ROOT),
              definition.merchant(),
              new BigDecimal(definition.amount()),
              definition.direction(),
              definition.status(),
              Instant.parse(definition.occurredAt())));
    }
    for (FixtureRow row : expected) {
      transactions.insertIfAbsent(row);
      FixtureRow actual =
          transactions
              .findForUpdate(row.id())
              .orElseThrow(
                  () -> new IllegalStateException("Transaction fixture row was not persisted."));
      if (!actual.equals(row))
        throw new IllegalStateException(
            "Existing transaction fixture data does not match the expected immutable state.");
    }
    log.info(
        "transaction.fixture action=ensure outcome=committed accountCount={}", accountIds.size());
    return expected.size();
  }

  static UUID stableId(UUID accountId, String fixtureKey) {
    return UUID.nameUUIDFromBytes(
        ("customer-transactions-v1:" + accountId + ":" + fixtureKey)
            .getBytes(StandardCharsets.UTF_8));
  }

  private record FixtureDefinition(
      String key,
      String merchant,
      String amount,
      TransactionDirection direction,
      TransactionStatus status,
      String occurredAt) {}
}
