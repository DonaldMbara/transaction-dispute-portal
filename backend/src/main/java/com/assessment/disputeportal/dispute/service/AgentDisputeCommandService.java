package com.assessment.disputeportal.dispute.service;

import com.assessment.disputeportal.common.exception.DisputeApiException;
import com.assessment.disputeportal.common.exception.InvalidOperationException;
import com.assessment.disputeportal.common.exception.InvalidRequestException;
import com.assessment.disputeportal.common.exception.ResourceNotFoundException;
import com.assessment.disputeportal.dispute.model.Dispute;
import com.assessment.disputeportal.dispute.model.DisputeEvent;
import com.assessment.disputeportal.dispute.model.DisputeStatus;
import com.assessment.disputeportal.dispute.model.dto.AgentDisputeDetailResponse;
import com.assessment.disputeportal.dispute.repository.AgentDisputeReadRepository;
import com.assessment.disputeportal.dispute.repository.AgentDisputeWriteRepository;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AgentDisputeCommandService {
  private final AgentDisputeWriteRepository writes;
  private final AgentDisputeReadRepository reads;

  @Transactional
  public AgentDisputeDetailResponse claim(
      UUID id, UUID agentId, UUID ifMatchDisputeId, long expectedVersion) {
    return transition(
        id,
        agentId,
        ifMatchDisputeId,
        expectedVersion,
        d -> d.claim(agentId, Instant.now()),
        null,
        null,
        String::strip);
  }

  @Transactional
  public AgentDisputeDetailResponse release(
      UUID id, UUID agentId, UUID ifMatchDisputeId, long expectedVersion, String reason) {
    return transition(
        id,
        agentId,
        ifMatchDisputeId,
        expectedVersion,
        d -> d.release(agentId, reason, Instant.now()),
        reason,
        null,
        Dispute::normalizeReleaseReason);
  }

  @Transactional
  public AgentDisputeDetailResponse resolve(
      UUID id,
      UUID agentId,
      UUID ifMatchDisputeId,
      long expectedVersion,
      String decision,
      String internalNotes,
      String customerMessage) {
    DisputeStatus status =
        switch (decision == null ? "" : decision) {
          case "ACCEPT" -> DisputeStatus.ACCEPTED;
          case "REJECT" -> DisputeStatus.REJECTED;
          default -> throw DisputeApiException.invalid();
        };
    return transition(
        id,
        agentId,
        ifMatchDisputeId,
        expectedVersion,
        d -> d.resolve(agentId, status, internalNotes, customerMessage, Instant.now()),
        internalNotes,
        customerMessage,
        String::strip);
  }

  private AgentDisputeDetailResponse transition(
      UUID id,
      UUID agentId,
      UUID ifMatchDisputeId,
      long expectedVersion,
      Function<Dispute, Dispute> operation,
      String internalNotes,
      String customerMessage,
      Function<String, String> normalizeNotes) {
    Dispute before = writes.lockById(id).orElseThrow(ResourceNotFoundException::new);
    if (!ifMatchDisputeId.equals(id) || expectedVersion != before.version()) {
      throw DisputeApiException.staleVersion();
    }
    final Dispute after;
    final String eventNotes;
    try {
      after = operation.apply(before);
      eventNotes = internalNotes == null ? null : normalizeNotes.apply(internalNotes);
    } catch (InvalidRequestException failure) {
      throw DisputeApiException.invalid();
    } catch (InvalidOperationException failure) {
      throw DisputeApiException.stateConflict();
    }
    if (!writes.update(before, after)) throw DisputeApiException.staleVersion();
    writes.append(
        DisputeEvent.transitioned(
            UUID.randomUUID(),
            before,
            after,
            agentId,
            eventNotes,
            customerMessage == null ? null : customerMessage.strip()));
    return reads.findById(id).orElseThrow(ResourceNotFoundException::new);
  }
}
