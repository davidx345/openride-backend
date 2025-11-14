package com.openride.admin.service;

import com.openride.admin.dto.SystemHealthMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for system health monitoring and metrics.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetricsService {

    private final JdbcTemplate jdbcTemplate;
    private final WebClient.Builder webClientBuilder;

    /**
     * Get system health metrics.
     * Cached for 5 minutes to reduce database load.
     */
    @Cacheable(value = "systemMetrics", unless = "#result == null")
    public SystemHealthMetrics getSystemHealthMetrics() {
        log.debug("Fetching system health metrics");

        try {
            // Query materialized view for quick metrics
            Map<String, Object> healthData = jdbcTemplate.queryForMap(
                    "SELECT * FROM mv_system_health LIMIT 1"
            );

            SystemHealthMetrics metrics = SystemHealthMetrics.builder()
                    .activeUsers(getLongValue(healthData, "active_users"))
                    .verifiedDrivers(getLongValue(healthData, "verified_drivers"))
                    .pendingBookings1h(getLongValue(healthData, "pending_bookings_1h"))
                    .todayConfirmedBookings(getLongValue(healthData, "today_confirmed_bookings"))
                    .pendingPayments1h(getLongValue(healthData, "pending_payments_1h"))
                    .openDisputes(getLongValue(healthData, "open_disputes"))
                    .activeSuspensions(getLongValue(healthData, "active_suspensions"))
                    .revenue24h(getBigDecimalValue(healthData, "revenue_24h"))
                    .auditEvents1h(getLongValue(healthData, "audit_events_1h"))
                    .lastUpdated(Instant.now().toString())
                    .services(checkServiceHealth())
                    .build();

            log.debug("System health metrics fetched successfully");
            return metrics;

        } catch (Exception e) {
            log.error("Error fetching system health metrics", e);
            return getDefaultMetrics();
        }
    }

    /**
     * Refresh materialized views manually.
     */
    public void refreshMetrics() {
        log.info("Manually refreshing metrics materialized views");

        try {
            jdbcTemplate.execute("SELECT refresh_admin_metrics()");
            log.info("Metrics refreshed successfully");
        } catch (Exception e) {
            log.error("Error refreshing metrics", e);
            throw new RuntimeException("Failed to refresh metrics", e);
        }
    }

    /**
     * Check health of dependent services.
     */
    private Map<String, SystemHealthMetrics.ServiceHealth> checkServiceHealth() {
        Map<String, SystemHealthMetrics.ServiceHealth> serviceHealth = new HashMap<>();

        // Check booking service
        serviceHealth.put("booking-service", checkService("http://localhost:8081/actuator/health"));

        // Check payment service
        serviceHealth.put("payment-service", checkService("http://localhost:8082/actuator/health"));

        // Check user service
        serviceHealth.put("user-service", checkService("http://localhost:8083/actuator/health"));

        // Check notification service
        serviceHealth.put("notification-service", checkService("http://localhost:8089/health"));

        return serviceHealth;
    }

    /**
     * Check individual service health.
     */
    private SystemHealthMetrics.ServiceHealth checkService(String healthUrl) {
        try {
            long startTime = System.currentTimeMillis();

            WebClient webClient = webClientBuilder.build();

            String response = webClient.get()
                    .uri(healthUrl)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(2))
                    .onErrorResume(e -> Mono.just("DOWN"))
                    .block();

            long responseTime = System.currentTimeMillis() - startTime;

            boolean isUp = response != null && response.contains("UP");

            return SystemHealthMetrics.ServiceHealth.builder()
                    .status(isUp ? "UP" : "DOWN")
                    .message(isUp ? "Service is healthy" : "Service is down or degraded")
                    .responseTimeMs(responseTime)
                    .build();

        } catch (Exception e) {
            log.warn("Error checking service health: {}", healthUrl, e);
            return SystemHealthMetrics.ServiceHealth.builder()
                    .status("DOWN")
                    .message("Unable to reach service")
                    .responseTimeMs(null)
                    .build();
        }
    }

    /**
     * Get booking metrics for a date range.
     */
    public Map<String, Object> getBookingMetrics(String fromDate, String toDate) {
        String sql = "SELECT * FROM mv_daily_booking_metrics " +
                     "WHERE metric_date >= ?::date AND metric_date <= ?::date " +
                     "ORDER BY metric_date DESC";

        var results = jdbcTemplate.queryForList(sql, fromDate, toDate);

        return Map.of(
                "dailyMetrics", results,
                "summary", calculateBookingSummary(results)
        );
    }

    /**
     * Get payment metrics for a date range.
     */
    public Map<String, Object> getPaymentMetrics(String fromDate, String toDate) {
        String sql = "SELECT * FROM mv_daily_payment_metrics " +
                     "WHERE metric_date >= ?::date AND metric_date <= ?::date " +
                     "ORDER BY metric_date DESC";

        var results = jdbcTemplate.queryForList(sql, fromDate, toDate);

        return Map.of(
                "dailyMetrics", results,
                "summary", calculatePaymentSummary(results)
        );
    }

    /**
     * Get user growth metrics.
     */
    public Map<String, Object> getUserGrowthMetrics(int days) {
        String sql = "SELECT * FROM mv_user_growth_metrics " +
                     "WHERE metric_date >= CURRENT_DATE - ? " +
                     "ORDER BY metric_date DESC";

        var results = jdbcTemplate.queryForList(sql, days);

        return Map.of("metrics", results);
    }

    // Helper methods

    private Long getLongValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return 0L;
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return 0L;
    }

    private BigDecimal getBigDecimalValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof Number) {
            return BigDecimal.valueOf(((Number) value).doubleValue());
        }
        return BigDecimal.ZERO;
    }

    private Map<String, Object> calculateBookingSummary(java.util.List<Map<String, Object>> results) {
        if (results.isEmpty()) {
            return Map.of();
        }

        long totalBookings = results.stream()
                .mapToLong(m -> getLongValue(m, "total_bookings"))
                .sum();

        long totalConfirmed = results.stream()
                .mapToLong(m -> getLongValue(m, "confirmed_bookings"))
                .sum();

        return Map.of(
                "totalBookings", totalBookings,
                "totalConfirmed", totalConfirmed,
                "confirmationRate", totalBookings > 0 ? (totalConfirmed * 100.0 / totalBookings) : 0.0
        );
    }

    private Map<String, Object> calculatePaymentSummary(java.util.List<Map<String, Object>> results) {
        if (results.isEmpty()) {
            return Map.of();
        }

        long totalPayments = results.stream()
                .mapToLong(m -> getLongValue(m, "total_payments"))
                .sum();

        long successfulPayments = results.stream()
                .mapToLong(m -> getLongValue(m, "successful_payments"))
                .sum();

        return Map.of(
                "totalPayments", totalPayments,
                "successfulPayments", successfulPayments,
                "successRate", totalPayments > 0 ? (successfulPayments * 100.0 / totalPayments) : 0.0
        );
    }

    private SystemHealthMetrics getDefaultMetrics() {
        return SystemHealthMetrics.builder()
                .activeUsers(0L)
                .verifiedDrivers(0L)
                .pendingBookings1h(0L)
                .todayConfirmedBookings(0L)
                .pendingPayments1h(0L)
                .openDisputes(0L)
                .activeSuspensions(0L)
                .revenue24h(BigDecimal.ZERO)
                .auditEvents1h(0L)
                .lastUpdated(Instant.now().toString())
                .services(Map.of())
                .build();
    }
}
