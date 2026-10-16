package com.assessment.disputeportal.dispute.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.assessment.disputeportal.common.exception.InvalidRequestException;
import com.assessment.disputeportal.dispute.exception.DisputeApiException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AgentIfMatchTest {
  private static final UUID ID = UUID.fromString("5f9868e0-1272-47ee-b0d2-ef33e4e1d331");

  @Test
  void parsesOneStrongTagAndSurroundingHttpWhitespace() {
    assertThat(AgentIfMatch.parse(List.of("  \"dispute-" + ID + "-v3\" \t")))
        .isEqualTo(new AgentIfMatch(ID, 3));
  }

  @Test
  void rejectsMissingMalformedDuplicateWeakWildcardListAndOverflowTags() {
    assertThatThrownBy(() -> AgentIfMatch.parse(List.of())).isInstanceOf(DisputeApiException.class);
    for (String tag :
        List.of(
            "W/\"dispute-" + ID + "-v3\"",
            "*",
            "\"dispute-" + ID + "-v3\", \"other\"",
            "\"dispute-" + ID + "-v-1\"",
            "\"dispute-" + ID + "-v9223372036854775808\"")) {
      assertThatThrownBy(() -> AgentIfMatch.parse(List.of(tag)))
          .isInstanceOf(InvalidRequestException.class);
    }
    assertThatThrownBy(
            () ->
                AgentIfMatch.parse(
                    List.of("\"dispute-" + ID + "-v1\"", "\"dispute-" + ID + "-v1\"")))
        .isInstanceOf(InvalidRequestException.class);
  }
}
