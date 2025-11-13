package com.openride.booking.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

/**
 * Booking configuration properties
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "booking")
public class BookingConfigProperties {

    private HoldConfig hold = new HoldConfig();
    private CancellationConfig cancellation = new CancellationConfig();
    private LockConfig lock = new LockConfig();
    private BigDecimal platformFeePercentage = new BigDecimal("0.05");
    private int maxSeatsPerBooking = 4;

    @Data
    public static class HoldConfig {
        private int ttlMinutes = 10;
        private int extensionMinutes = 15;
    }

    @Data
    public static class CancellationConfig {
        private int fullRefundHours = 24;
        private int partialRefundHours = 6;
        private BigDecimal partialRefundPercentage = new BigDecimal("0.50");
    }

    @Data
    public static class LockConfig {
        private long waitTimeSeconds = 5;
        private long leaseTimeSeconds = 10;
    }
}
