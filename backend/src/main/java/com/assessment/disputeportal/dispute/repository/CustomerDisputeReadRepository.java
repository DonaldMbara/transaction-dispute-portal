package com.assessment.disputeportal.dispute.repository;

import com.assessment.disputeportal.dispute.model.dto.CustomerDisputeDetailDto;
import com.assessment.disputeportal.dispute.model.dto.CustomerDisputeListItemDto;
import com.assessment.disputeportal.dispute.model.projection.EventProjection;
import com.assessment.disputeportal.dispute.model.query.DisputeQuery;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;

/** Read-only repository for customer-scoped dispute queries. All queries enforce ownership. */
public interface CustomerDisputeReadRepository {

  /**
   * Returns an ownership-scoped, filtered and paginated list of disputes. Ownership is enforced
   * through dispute → transaction → account → customer_id.
   */
  Page<CustomerDisputeListItemDto> findOwned(DisputeQuery query);

  /** Returns whether the requested account belongs to the authenticated customer. */
  boolean accountIsOwnedByCustomer(UUID accountId, UUID customerId);

  /**
   * Returns a single dispute detail DTO, or empty if the dispute does not exist or does not belong
   * to the authenticated customer.
   */
  Optional<CustomerDisputeDetailDto> findOwnedById(UUID disputeId, UUID customerId);

  /**
   * Returns customer-safe event projections for a dispute, ordered by dispute_version ASC,
   * created_at ASC. Excludes internal_notes, actor_id, previous_agent_id, new_agent_id.
   */
  List<EventProjection> findEventsByDisputeId(UUID disputeId);
}
