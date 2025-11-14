package com.openride.payouts.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

/**
 * Financial configuration properties.
 */
@Configuration
@ConfigurationProperties(prefix = "financial")
@Data
public class FinancialConfig {

    private Commission commission = new Commission();
    private Payout payout = new Payout();
    private Wallet wallet = new Wallet();

    @Data
    public static class Commission {
        /**
         * Platform commission rate (0.15 = 15%)
         */
        private BigDecimal platformRate = new BigDecimal("0.15");

        /**
         * Calculate driver earnings from trip price.
         * 
         * @param tripPrice Total trip price
         * @return Driver earnings after commission
         */
        public BigDecimal calculateDriverEarnings(BigDecimal tripPrice) {
            BigDecimal driverRate = BigDecimal.ONE.subtract(platformRate);
            return tripPrice.multiply(driverRate).setScale(2, java.math.RoundingMode.HALF_UP);
        }

        /**
         * Calculate platform commission from trip price.
         * 
         * @param tripPrice Total trip price
         * @return Platform commission amount
         */
        public BigDecimal calculatePlatformCommission(BigDecimal tripPrice) {
            return tripPrice.multiply(platformRate).setScale(2, java.math.RoundingMode.HALF_UP);
        }
    }

    @Data
    public static class Payout {
        /**
         * Minimum payout amount (₦5,000)
         */
        private BigDecimal minimumAmount = new BigDecimal("5000.00");

        /**
         * Auto-settlement enabled
         */
        private boolean autoSettlementEnabled = false;

        /**
         * Settlement schedule cron expression
         */
        private String settlementScheduleCron = "0 0 2 * * MON";

        /**
         * Maximum pending requests per driver
         */
        private int maxPendingRequestsPerDriver = 1;
    }

    @Data
    public static class Wallet {
        /**
         * Initial wallet balance
         */
        private BigDecimal initialBalance = BigDecimal.ZERO;

        /**
         * Allow negative balance
         */
        private boolean allowNegativeBalance = false;
    }
}
