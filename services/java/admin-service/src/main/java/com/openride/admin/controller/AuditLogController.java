package com.openride.admin.controller;

import com.openride.admin.dto.AuditLogResponse;
import com.openride.admin.service.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Controller for audit log viewing.
 */
@Slf4j
@RestController
@RequestMapping("/v1/admin/audit-logs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - Audit Logs", description = "Audit log viewing and filtering endpoints")
@SecurityRequirement(name = "bearerAuth")
public class AuditLogController {

    private final AuditService auditService;

    /**
     * Get audit logs by entity.
     */
    @GetMapping("/entity")
    @Operation(summary = "Get audit logs by entity", description = "Get audit trail for a specific entity")
    public ResponseEntity<Page<AuditLogResponse>> getAuditLogsByEntity(
            @RequestParam String entityType,
            @RequestParam String entityId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        log.info("Fetching audit logs for entity: {}/{}", entityType, entityId);

        Page<AuditLogResponse> logs = auditService.getAuditLogsByEntity(entityType, entityId, page, size);

        return ResponseEntity.ok(logs);
    }

    /**
     * Get audit logs by actor.
     */
    @GetMapping("/actor/{actorId}")
    @Operation(summary = "Get audit logs by actor", description = "Get all actions performed by a specific user")
    public ResponseEntity<Page<AuditLogResponse>> getAuditLogsByActor(
            @PathVariable UUID actorId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        log.info("Fetching audit logs for actor: {}", actorId);

        Page<AuditLogResponse> logs = auditService.getAuditLogsByActor(actorId, page, size);

        return ResponseEntity.ok(logs);
    }

    /**
     * Get audit logs by action type.
     */
    @GetMapping("/action")
    @Operation(summary = "Get audit logs by action", description = "Get all logs for a specific action type")
    public ResponseEntity<Page<AuditLogResponse>> getAuditLogsByAction(
            @RequestParam String action,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        log.info("Fetching audit logs for action: {}", action);

        Page<AuditLogResponse> logs = auditService.getAuditLogsByAction(action, page, size);

        return ResponseEntity.ok(logs);
    }

    /**
     * Get audit logs by date range.
     */
    @GetMapping("/date-range")
    @Operation(summary = "Get audit logs by date range", description = "Get audit logs within a specific time period")
    public ResponseEntity<Page<AuditLogResponse>> getAuditLogsByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        log.info("Fetching audit logs from {} to {}", from, to);

        Instant fromInstant = from.toInstant(ZoneOffset.UTC);
        Instant toInstant = to.toInstant(ZoneOffset.UTC);

        Page<AuditLogResponse> logs = auditService.getAuditLogsByDateRange(fromInstant, toInstant, page, size);

        return ResponseEntity.ok(logs);
    }

    /**
     * Get recent admin actions.
     */
    @GetMapping("/recent")
    @Operation(summary = "Get recent admin actions", description = "Get recent actions performed by admins")
    public ResponseEntity<List<AuditLogResponse>> getRecentAdminActions(
            @RequestParam(defaultValue = "24") int hours,
            @RequestParam(defaultValue = "50") int limit
    ) {
        log.info("Fetching recent admin actions: last {} hours, limit {}", hours, limit);

        List<AuditLogResponse> logs = auditService.getRecentAdminActions(hours, limit);

        return ResponseEntity.ok(logs);
    }
}
