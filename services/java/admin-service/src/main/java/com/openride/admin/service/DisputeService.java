package com.openride.admin.service;

import com.openride.admin.dto.CreateDisputeRequest;
import com.openride.admin.dto.DisputeResponse;
import com.openride.admin.dto.ResolveDisputeRequest;
import com.openride.admin.model.Dispute;
import com.openride.admin.repository.DisputeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for dispute management operations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DisputeService {

    private final DisputeRepository disputeRepository;
    private final AuditService auditService;

    /**
     * Create a new dispute.
     */
    @Transactional
    public DisputeResponse createDispute(CreateDisputeRequest request) {
        log.info("Creating dispute for booking: {}", request.getBookingId());

        Dispute dispute = Dispute.builder()
                .bookingId(request.getBookingId())
                .reporterId(request.getReporterId())
                .reportedId(request.getReportedId())
                .disputeType(Dispute.DisputeType.valueOf(request.getDisputeType().toUpperCase()))
                .status(Dispute.DisputeStatus.OPEN)
                .subject(request.getSubject())
                .description(request.getDescription())
                .evidenceUrls(request.getEvidenceUrls())
                .build();

        Dispute savedDispute = disputeRepository.save(dispute);

        // Log to audit trail
        auditService.logAction(
                "DISPUTE",
                savedDispute.getId().toString(),
                "CREATE",
                request.getReporterId(),
                String.format("Created dispute: %s", request.getSubject())
        );

        log.info("Created dispute: {}", savedDispute.getId());

        return DisputeResponse.fromEntity(savedDispute);
    }

    /**
     * Get all open disputes.
     */
    @Transactional(readOnly = true)
    public Page<DisputeResponse> getOpenDisputes(int page, int size) {
        log.debug("Fetching open disputes, page: {}, size: {}", page, size);

        Pageable pageable = PageRequest.of(page, size);
        Page<Dispute> disputes = disputeRepository.findOpenDisputes(pageable);

        return disputes.map(DisputeResponse::fromEntity);
    }

    /**
     * Get disputes by status.
     */
    @Transactional(readOnly = true)
    public Page<DisputeResponse> getDisputesByStatus(String status, int page, int size) {
        log.debug("Fetching disputes by status: {}", status);

        Dispute.DisputeStatus disputeStatus = Dispute.DisputeStatus.valueOf(status.toUpperCase());
        Pageable pageable = PageRequest.of(page, size);
        Page<Dispute> disputes = disputeRepository.findByStatusOrderByCreatedAtDesc(disputeStatus, pageable);

        return disputes.map(DisputeResponse::fromEntity);
    }

    /**
     * Get dispute by ID.
     */
    @Transactional(readOnly = true)
    public DisputeResponse getDisputeById(UUID disputeId) {
        log.debug("Fetching dispute: {}", disputeId);

        Dispute dispute = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new IllegalArgumentException("Dispute not found: " + disputeId));

        return DisputeResponse.fromEntity(dispute);
    }

    /**
     * Resolve a dispute.
     */
    @Transactional
    public DisputeResponse resolveDispute(UUID disputeId, ResolveDisputeRequest request, UUID adminId) {
        log.info("Resolving dispute: {}", disputeId);

        Dispute dispute = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new IllegalArgumentException("Dispute not found: " + disputeId));

        // Validate status transition
        if (dispute.getStatus() != Dispute.DisputeStatus.OPEN && 
            dispute.getStatus() != Dispute.DisputeStatus.IN_PROGRESS) {
            throw new IllegalStateException("Cannot resolve dispute with status: " + dispute.getStatus());
        }

        // Update dispute
        Dispute.DisputeStatus newStatus = Dispute.DisputeStatus.valueOf(request.getStatus().toUpperCase());
        dispute.setStatus(newStatus);
        dispute.setResolutionNotes(request.getResolutionNotes());
        dispute.setResolvedBy(adminId);
        dispute.setResolvedAt(Instant.now());

        Dispute savedDispute = disputeRepository.save(dispute);

        // Log to audit trail
        auditService.logAction(
                "DISPUTE",
                disputeId.toString(),
                "RESOLVE",
                adminId,
                String.format("Resolved dispute with status: %s", newStatus)
        );

        log.info("Resolved dispute: {} with status: {}", disputeId, newStatus);

        return DisputeResponse.fromEntity(savedDispute);
    }

    /**
     * Assign dispute to admin (move to IN_PROGRESS).
     */
    @Transactional
    public DisputeResponse assignDispute(UUID disputeId, UUID adminId) {
        log.info("Assigning dispute {} to admin {}", disputeId, adminId);

        Dispute dispute = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new IllegalArgumentException("Dispute not found: " + disputeId));

        if (dispute.getStatus() != Dispute.DisputeStatus.OPEN) {
            throw new IllegalStateException("Can only assign OPEN disputes");
        }

        dispute.setStatus(Dispute.DisputeStatus.IN_PROGRESS);
        Dispute savedDispute = disputeRepository.save(dispute);

        // Log to audit trail
        auditService.logAction(
                "DISPUTE",
                disputeId.toString(),
                "ASSIGN",
                adminId,
                "Assigned dispute to admin for investigation"
        );

        return DisputeResponse.fromEntity(savedDispute);
    }

    /**
     * Get disputes for a specific booking.
     */
    @Transactional(readOnly = true)
    public List<DisputeResponse> getDisputesByBooking(UUID bookingId) {
        log.debug("Fetching disputes for booking: {}", bookingId);

        List<Dispute> disputes = disputeRepository.findByBookingIdOrderByCreatedAtDesc(bookingId);

        return disputes.stream()
                .map(DisputeResponse::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * Get dispute statistics.
     */
    @Transactional(readOnly = true)
    public java.util.Map<String, Long> getDisputeStatistics() {
        return java.util.Map.of(
                "open", disputeRepository.countByStatus(Dispute.DisputeStatus.OPEN),
                "inProgress", disputeRepository.countByStatus(Dispute.DisputeStatus.IN_PROGRESS),
                "resolved", disputeRepository.countByStatus(Dispute.DisputeStatus.RESOLVED),
                "rejected", disputeRepository.countByStatus(Dispute.DisputeStatus.REJECTED)
        );
    }
}
