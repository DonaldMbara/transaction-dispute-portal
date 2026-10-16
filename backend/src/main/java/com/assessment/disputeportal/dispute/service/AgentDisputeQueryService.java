package com.assessment.disputeportal.dispute.service;

import com.assessment.disputeportal.common.exception.ResourceNotFoundException;
import com.assessment.disputeportal.dispute.model.dto.AgentDisputeDetailDto;
import com.assessment.disputeportal.dispute.model.dto.AgentDisputeListItemDto;
import com.assessment.disputeportal.dispute.model.query.AgentDisputeQuery;
import com.assessment.disputeportal.dispute.repository.AgentDisputeReadRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AgentDisputeQueryService {
  private final AgentDisputeReadRepository repository;

  public Page<AgentDisputeListItemDto> list(AgentDisputeQuery query) {
    return repository.find(query);
  }

  public AgentDisputeDetailDto detail(UUID id) {
    return repository.findById(id).orElseThrow(ResourceNotFoundException::new);
  }
}
