package com.openride.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

/**
 * DTO for system health metrics.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemHealthMetrics {

    private Long activeUsers;
    private Long verifiedDrivers;
    private Long pendingBookings1h;
    private Long todayConfirmedBookings;
    private Long pendingPayments1h;
    private Long openDisputes;
    private Long activeSuspensions;
    private BigDecimal revenue24h;
    private Long auditEvents1h;
    private String lastUpdated;
    
    // Service health status
    private Map<String, ServiceHealth> services;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ServiceHealth {
        private String status;  // UP, DOWN, DEGRADED
        private String message;
        private Long responseTimeMs;
    }
}
