package com.openride.admin.controller;

import com.openride.admin.dto.SystemHealthMetrics;
import com.openride.admin.service.MetricsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller for system health and metrics dashboard.
 */
@Slf4j
@RestController
@RequestMapping("/v1/admin/metrics")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - Metrics", description = "System health and metrics dashboard endpoints")
@SecurityRequirement(name = "bearerAuth")
public class MetricsController {

    private final MetricsService metricsService;

    /**
     * Get real-time system health metrics.
     */
    @GetMapping("/realtime")
    @Operation(summary = "Get real-time metrics", description = "Get current system health and performance metrics")
    public ResponseEntity<SystemHealthMetrics> getRealtimeMetrics() {
        log.info("Fetching real-time system metrics");

        SystemHealthMetrics metrics = metricsService.getSystemHealthMetrics();

        return ResponseEntity.ok(metrics);
    }

    /**
     * Manually refresh metrics.
     */
    @PostMapping("/refresh")
    @Operation(summary = "Refresh metrics", description = "Manually refresh materialized views for metrics")
    public ResponseEntity<Map<String, String>> refreshMetrics() {
        log.info("Admin manually refreshing metrics");

        metricsService.refreshMetrics();

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Metrics refreshed successfully"
        ));
    }

    /**
     * Get booking metrics for date range.
     */
    @GetMapping("/bookings")
    @Operation(summary = "Get booking metrics", description = "Get aggregated booking metrics for a date range")
    public ResponseEntity<Map<String, Object>> getBookingMetrics(
            @RequestParam String fromDate,
            @RequestParam String toDate
    ) {
        log.info("Fetching booking metrics from {} to {}", fromDate, toDate);

        Map<String, Object> metrics = metricsService.getBookingMetrics(fromDate, toDate);

        return ResponseEntity.ok(metrics);
    }

    /**
     * Get payment metrics for date range.
     */
    @GetMapping("/payments")
    @Operation(summary = "Get payment metrics", description = "Get aggregated payment metrics for a date range")
    public ResponseEntity<Map<String, Object>> getPaymentMetrics(
            @RequestParam String fromDate,
            @RequestParam String toDate
    ) {
        log.info("Fetching payment metrics from {} to {}", fromDate, toDate);

        Map<String, Object> metrics = metricsService.getPaymentMetrics(fromDate, toDate);

        return ResponseEntity.ok(metrics);
    }

    /**
     * Get user growth metrics.
     */
    @GetMapping("/user-growth")
    @Operation(summary = "Get user growth metrics", description = "Get user registration and growth metrics")
    public ResponseEntity<Map<String, Object>> getUserGrowthMetrics(
            @RequestParam(defaultValue = "30") int days
    ) {
        log.info("Fetching user growth metrics for last {} days", days);

        Map<String, Object> metrics = metricsService.getUserGrowthMetrics(days);

        return ResponseEntity.ok(metrics);
    }
}
