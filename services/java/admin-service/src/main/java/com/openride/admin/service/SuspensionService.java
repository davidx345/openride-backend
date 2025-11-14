package com.openride.admin.service;

import com.openride.admin.dto.CreateSuspensionRequest;
import com.openride.admin.dto.UserSuspensionResponse;
import com.openride.admin.model.UserSuspension;
import com.openride.admin.repository.UserSuspensionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for user suspension/ban management.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SuspensionService {

    private final UserSuspensionRepository suspensionRepository;
    private final AuditService auditService;

    /**
     * Create a new user suspension.
     */
    @Transactional
    public UserSuspensionResponse suspendUser(CreateSuspensionRequest request, UUID adminId) {
        log.info("Suspending user: {}", request.getUserId());

        // Validate suspension type and end date
        UserSuspension.SuspensionType type = UserSuspension.SuspensionType.valueOf(
                request.getSuspensionType().toUpperCase()
        );

        if (type == UserSuspension.SuspensionType.TEMPORARY && request.getEndDate() == null) {
            throw new IllegalArgumentException("End date is required for temporary suspension");
        }

        if (type == UserSuspension.SuspensionType.PERMANENT && request.getEndDate() != null) {
            throw new IllegalArgumentException("End date must be null for permanent suspension");
        }

        // Check if user already has an active suspension
        Optional<UserSuspension> existingSuspension = suspensionRepository.findActiveSuspensionForUser(
                request.getUserId(),
                Instant.now()
        );

        if (existingSuspension.isPresent()) {
            throw new IllegalStateException("User already has an active suspension");
        }

        // Create suspension
        UserSuspension suspension = UserSuspension.builder()
                .userId(request.getUserId())
                .suspensionType(type)
                .reason(request.getReason())
                .notes(request.getNotes())
                .startDate(Instant.now())
                .endDate(request.getEndDate())
                .suspendedBy(adminId)
                .isActive(true)
                .build();

        UserSuspension savedSuspension = suspensionRepository.save(suspension);

        // Log to audit trail
        auditService.logAction(
                "USER_SUSPENSION",
                savedSuspension.getId().toString(),
                "CREATE",
                adminId,
                String.format("Suspended user %s - %s: %s", 
                        request.getUserId(), type, request.getReason())
        );

        log.info("Created suspension: {} for user: {}", savedSuspension.getId(), request.getUserId());

        return UserSuspensionResponse.fromEntity(savedSuspension);
    }

    /**
     * Lift a suspension (deactivate it).
     */
    @Transactional
    public UserSuspensionResponse liftSuspension(UUID suspensionId, UUID adminId) {
        log.info("Lifting suspension: {}", suspensionId);

        UserSuspension suspension = suspensionRepository.findById(suspensionId)
                .orElseThrow(() -> new IllegalArgumentException("Suspension not found: " + suspensionId));

        if (!suspension.getIsActive()) {
            throw new IllegalStateException("Suspension is already inactive");
        }

        suspension.setIsActive(false);
        UserSuspension savedSuspension = suspensionRepository.save(suspension);

        // Log to audit trail
        auditService.logAction(
                "USER_SUSPENSION",
                suspensionId.toString(),
                "LIFT",
                adminId,
                String.format("Lifted suspension for user: %s", suspension.getUserId())
        );

        log.info("Lifted suspension: {} for user: {}", suspensionId, suspension.getUserId());

        return UserSuspensionResponse.fromEntity(savedSuspension);
    }

    /**
     * Check if a user is currently suspended.
     */
    @Transactional(readOnly = true)
    public boolean isUserSuspended(UUID userId) {
        Optional<UserSuspension> suspension = suspensionRepository.findActiveSuspensionForUser(
                userId,
                Instant.now()
        );

        return suspension.isPresent();
    }

    /**
     * Get active suspension for a user.
     */
    @Transactional(readOnly = true)
    public Optional<UserSuspensionResponse> getActiveSuspension(UUID userId) {
        return suspensionRepository.findActiveSuspensionForUser(userId, Instant.now())
                .map(UserSuspensionResponse::fromEntity);
    }

    /**
     * Get all suspensions for a user.
     */
    @Transactional(readOnly = true)
    public List<UserSuspensionResponse> getUserSuspensions(UUID userId) {
        return suspensionRepository.findByUserIdOrderByStartDateDesc(userId).stream()
                .map(UserSuspensionResponse::fromEntity)
                .toList();
    }

    /**
     * Get all active suspensions.
     */
    @Transactional(readOnly = true)
    public Page<UserSuspensionResponse> getActiveSuspensions(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<UserSuspension> suspensions = suspensionRepository.findAllActiveSuspensions(
                Instant.now(),
                pageable
        );

        return suspensions.map(UserSuspensionResponse::fromEntity);
    }

    /**
     * Scheduled job to deactivate expired temporary suspensions.
     * Runs every hour.
     */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void deactivateExpiredSuspensions() {
        log.info("Running scheduled job to deactivate expired suspensions");

        List<UserSuspension> expiredSuspensions = suspensionRepository.findExpiredSuspensions(Instant.now());

        if (expiredSuspensions.isEmpty()) {
            log.info("No expired suspensions found");
            return;
        }

        for (UserSuspension suspension : expiredSuspensions) {
            suspension.setIsActive(false);
            suspensionRepository.save(suspension);

            log.info("Deactivated expired suspension: {} for user: {}", 
                    suspension.getId(), suspension.getUserId());
        }

        log.info("Deactivated {} expired suspensions", expiredSuspensions.size());
    }
}
