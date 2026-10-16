package com.assessment.disputeportal.account.model;

public enum AccountType {
  MAIN("Main Account"),
  SAVINGS("Savings Account");

  private final String displayName;

  AccountType(String displayName) {
    this.displayName = displayName;
  }

  public String displayName() {
    return displayName;
  }
}
