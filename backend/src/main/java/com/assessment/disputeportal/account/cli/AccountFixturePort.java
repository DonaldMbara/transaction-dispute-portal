package com.assessment.disputeportal.account.cli;

import java.util.Map;
import java.util.UUID;

/** Narrow fixture-only contract consumed by later feature fixtures. */
public interface AccountFixturePort {
  Map<String, UUID> getFixtureAccountIds(String customerEmail);
}
