package com.assessment.disputeportal.dispute.service;

import com.assessment.disputeportal.common.exception.ResourceNotFoundException;
import com.assessment.disputeportal.dispute.model.dto.AgentDisputeDetailResponse;
import com.assessment.disputeportal.dispute.model.dto.AgentDisputeListItemResponse;
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

  public Page<AgentDisputeListItemResponse> list(AgentDisputeQuery query) {
    return repository.find(query);
  }

  public AgentDisputeDetailResponse detail(UUID id) {
    return repository.findById(id).orElseThrow(ResourceNotFoundException::new);
  }
}
