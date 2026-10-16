package com.assessment.disputeportal.dispute.service;

import com.assessment.disputeportal.common.exception.InvalidRequestException;
import com.assessment.disputeportal.dispute.model.AgentQueue;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentDisputeQueryParserTest {
    private final AgentDisputeQueryParser parser = new AgentDisputeQueryParser(new DisputeQueryParser());
    private final UUID agent = UUID.randomUUID();

    @Test
    void defaultsToUnassignedAndParsesTheSharedFilters() {
        var query = parser.parse(Map.of("page", new String[]{"2"}, "search", new String[]{"DSP-1234"}), agent);
        assertThat(query.queue()).isEqualTo(AgentQueue.UNASSIGNED);
        assertThat(query.agentId()).isEqualTo(agent);
        assertThat(query.filters().pageRequest().getPageNumber()).isEqualTo(1);
        assertThat(query.filters().refSearchPattern()).isEqualTo("1234%");
    }

    @Test
    void supportsAllExplicitQueuesAndRejectsUnknownValues() {
        assertThat(parser.parse(Map.of("queue", new String[]{"mine"}), agent).queue()).isEqualTo(AgentQueue.MINE);
        assertThat(parser.parse(Map.of("queue", new String[]{"all"}), agent).queue()).isEqualTo(AgentQueue.ALL);
        assertThatThrownBy(() -> parser.parse(Map.of("queue", new String[]{"other"}), agent))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> parser.parse(Map.of("reason", new String[]{"NOT_A_REASON"}), agent))
                .isInstanceOf(InvalidRequestException.class);
    }
}
