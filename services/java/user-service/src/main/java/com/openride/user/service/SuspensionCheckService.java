package com.openride.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Service for checking user suspension status.
 * Used to validate users before critical operations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SuspensionCheckService {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Check if a user is currently suspended.
     *
     * @param userId the user ID to check
     * @return true if user is suspended, false otherwise
     */
    @Transactional(readOnly = true)
    public boolean isUserSuspended(UUID userId) {
        try {
            String sql = "SELECT is_user_suspended(?)";
            Boolean result = jdbcTemplate.queryForObject(sql, Boolean.class, userId);
            
            if (Boolean.TRUE.equals(result)) {
                log.warn("User {} is currently suspended", userId);
            }
            
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.error("Error checking suspension status for user {}", userId, e);
            return false;
        }
    }

    /**
     * Get the active suspension details for a user.
     *
     * @param userId the user ID
     * @return suspension details or null if not suspended
     */
    @Transactional(readOnly = true)
    public SuspensionDetails getActiveSuspension(UUID userId) {
        try {
            String sql = """
                    SELECT 
                        id,
                        suspension_type,
                        reason,
                        start_date,
                        end_date,
                        suspended_by
                    FROM user_suspensions
                    WHERE user_id = ?
                      AND is_active = true
                      AND start_date <= CURRENT_TIMESTAMP
                      AND (end_date IS NULL OR end_date > CURRENT_TIMESTAMP)
                    ORDER BY created_at DESC
                    LIMIT 1
                    """;

            return jdbcTemplate.query(sql, rs -> {
                if (rs.next()) {
                    return SuspensionDetails.builder()
                            .suspensionId(UUID.fromString(rs.getString("id")))
                            .suspensionType(rs.getString("suspension_type"))
                            .reason(rs.getString("reason"))
                            .startDate(rs.getTimestamp("start_date").toInstant())
                            .endDate(rs.getTimestamp("end_date") != null ? 
                                    rs.getTimestamp("end_date").toInstant() : null)
                            .suspendedBy(UUID.fromString(rs.getString("suspended_by")))
                            .build();
                }
                return null;
            }, userId);
        } catch (Exception e) {
            log.error("Error getting active suspension for user {}", userId, e);
            return null;
        }
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class SuspensionDetails {
        private UUID suspensionId;
        private String suspensionType;
        private String reason;
        private java.time.Instant startDate;
        private java.time.Instant endDate;
        private UUID suspendedBy;
    }
}
