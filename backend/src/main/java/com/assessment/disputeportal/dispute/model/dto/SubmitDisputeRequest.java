package com.assessment.disputeportal.dispute.model.dto;

import java.util.UUID;

import com.assessment.disputeportal.dispute.model.DisputeReason;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public final class SubmitDisputeRequest {
    private UUID transactionId;
    private DisputeReason reason;
    private String description;
    @JsonIgnore private boolean hasUnknownFields;

    @JsonProperty("transactionId") public void setTransactionId(UUID transactionId) { this.transactionId = transactionId; }
    @JsonProperty("reason") public void setReason(DisputeReason reason) { this.reason = reason; }
    @JsonProperty("description") public void setDescription(String description) { this.description = description; }
    @JsonAnySetter public void unknown(String ignoredName, Object ignoredValue) { hasUnknownFields = true; }
    public boolean hasUnknownFields() { return hasUnknownFields; }
}
