package com.assessment.disputeportal.account.cli;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AccountFixtureCommandTest {
    @Test
    void rejectsMissingOrMalformedOptInArgumentsWithoutStartingApplication() {
        assertThat(AccountFixtureCommand.launch(new String[]{})).isEqualTo(2);
        assertThat(AccountFixtureCommand.launch(new String[] {"--email", "a@example.test", "extra"})).isEqualTo(2);
        assertThat(AccountFixtureCommand.launch(new String[] {"--email", " "})).isEqualTo(2);
    }
}
