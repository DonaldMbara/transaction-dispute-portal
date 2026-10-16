package com.assessment.disputeportal.dispute.service;

import com.assessment.disputeportal.common.exception.ResourceNotFoundException;
import com.assessment.disputeportal.dispute.mapper.CustomerTimelineMapper;
import com.assessment.disputeportal.dispute.model.dto.CustomerDisputeDetailDto;
import com.assessment.disputeportal.dispute.model.dto.CustomerDisputeListItemDto;
import com.assessment.disputeportal.dispute.model.dto.CustomerTimelineEntry;
import com.assessment.disputeportal.dispute.model.query.DisputeQuery;
import com.assessment.disputeportal.dispute.model.projection.EventProjection;
import com.assessment.disputeportal.dispute.repository.CustomerDisputeReadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Read-only service for customer dispute queries.
 *
 * <p>Owns the read transaction boundary; delegates ownership-scoped SQL to the repository
 * and timeline curation to {@link CustomerTimelineMapper}.</p>
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DisputeQueryService {

    private final CustomerDisputeReadRepository repository;
    private final CustomerTimelineMapper timelineMapper;

    /**
     * Returns a paginated, ownership-scoped and filtered page of dispute list items.
     */
    public Page<CustomerDisputeListItemDto> getCustomerDisputePage(DisputeQuery query) {
        if (query.accountId() != null
                && !repository.accountIsOwnedByCustomer(query.accountId(), query.authenticatedCustomerId())) {
            throw new ResourceNotFoundException();
        }
        return repository.findOwned(query);
    }

    /**
     * Returns the full dispute detail for an owned dispute, including the curated timeline.
     *
     * @throws ResourceNotFoundException if the dispute does not exist or is not owned by the customer
     */
    public CustomerDisputeDetailDto getCustomerDisputeDetail(UUID disputeId, UUID customerId) {
        CustomerDisputeDetailDto base = repository.findOwnedById(disputeId, customerId)
                .orElseThrow(ResourceNotFoundException::new);
        List<EventProjection> events = repository.findEventsByDisputeId(disputeId);
        List<CustomerTimelineEntry> timeline = timelineMapper.map(events);
        return new CustomerDisputeDetailDto(
                base.id(),
                base.transaction(),
                base.reason(),
                base.status(),
                base.createdAt(),
                base.updatedAt(),
                base.resolvedAt(),
                base.description(),
                base.decision(),
                timeline);
    }
}
