package com.openride.admin.dto;

import com.openride.admin.model.Dispute;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO for Dispute response.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DisputeResponse {

    private UUID id;
    private UUID bookingId;
    private UUID reporterId;
    private UUID reportedId;
    private String disputeType;
    private String status;
    private String subject;
    private String description;
    private String[] evidenceUrls;
    private String resolutionNotes;
    private UUID resolvedBy;
    private Instant resolvedAt;
    private Instant createdAt;
    private Instant updatedAt;

    public static DisputeResponse fromEntity(Dispute dispute) {
        return DisputeResponse.builder()
                .id(dispute.getId())
                .bookingId(dispute.getBookingId())
                .reporterId(dispute.getReporterId())
                .reportedId(dispute.getReportedId())
                .disputeType(dispute.getDisputeType().name())
                .status(dispute.getStatus().name())
                .subject(dispute.getSubject())
                .description(dispute.getDescription())
                .evidenceUrls(dispute.getEvidenceUrls())
                .resolutionNotes(dispute.getResolutionNotes())
                .resolvedBy(dispute.getResolvedBy())
                .resolvedAt(dispute.getResolvedAt())
                .createdAt(dispute.getCreatedAt())
                .updatedAt(dispute.getUpdatedAt())
                .build();
    }
}
