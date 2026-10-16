package com.assessment.disputeportal.dispute.repository;

import com.assessment.disputeportal.dispute.model.dto.AgentDisputeDetailDto;
import com.assessment.disputeportal.dispute.model.dto.AgentDisputeListItemDto;
import com.assessment.disputeportal.dispute.model.query.AgentDisputeQuery;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;

public interface AgentDisputeReadRepository {
  Page<AgentDisputeListItemDto> find(AgentDisputeQuery query);

  Optional<AgentDisputeDetailDto> findById(UUID disputeId);
}
