package com.openride.payouts.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for FinancialConfig.
 */
@SpringBootTest(classes = {FinancialConfig.class})
@EnableConfigurationProperties(FinancialConfig.class)
@TestPropertySource(properties = {
        "payouts.financial.commission.platform-rate=0.15",
        "payouts.financial.payout.minimum-amount=5000.00",
        "payouts.financial.settlement.schedule=WEEKLY",
        "payouts.financial.settlement.day-of-week=MONDAY",
        "payouts.financial.settlement.time=02:00"
})
class FinancialConfigTest {

    @Autowired
    private FinancialConfig financialConfig;

    @Test
    void config_ShouldLoadCommissionProperties() {
        // Assert
        FinancialConfig.Commission commission = financialConfig.getCommission();
        
        assertThat(commission).isNotNull();
        assertThat(commission.getPlatformRate()).isEqualByComparingTo(BigDecimal.valueOf(0.15));
    }

    @Test
    void config_ShouldLoadPayoutProperties() {
        // Assert
        FinancialConfig.Payout payout = financialConfig.getPayout();
        
        assertThat(payout).isNotNull();
        assertThat(payout.getMinimumAmount()).isEqualByComparingTo(BigDecimal.valueOf(5000.00));
    }

    @Test
    void config_ShouldLoadSettlementProperties() {
        // Assert
        FinancialConfig.Settlement settlement = financialConfig.getSettlement();
        
        assertThat(settlement).isNotNull();
        assertThat(settlement.getSchedule()).isEqualTo("WEEKLY");
        assertThat(settlement.getDayOfWeek()).isEqualTo("MONDAY");
        assertThat(settlement.getTime()).isEqualTo("02:00");
    }

    @Test
    void calculateDriverEarnings_WithStandardCommission_ShouldCalculateCorrectly() {
        // Arrange
        BigDecimal totalPrice = BigDecimal.valueOf(10000.00);

        // Act
        BigDecimal driverEarnings = financialConfig.calculateDriverEarnings(totalPrice);

        // Assert
        assertThat(driverEarnings).isEqualByComparingTo(BigDecimal.valueOf(8500.00)); // 85% of 10000
    }

    @Test
    void calculatePlatformCommission_WithStandardRate_ShouldCalculateCorrectly() {
        // Arrange
        BigDecimal totalPrice = BigDecimal.valueOf(10000.00);

        // Act
        BigDecimal commission = financialConfig.calculatePlatformCommission(totalPrice);

        // Assert
        assertThat(commission).isEqualByComparingTo(BigDecimal.valueOf(1500.00)); // 15% of 10000
    }

    @Test
    void calculateDriverEarnings_WithZeroAmount_ShouldReturnZero() {
        // Arrange
        BigDecimal totalPrice = BigDecimal.ZERO;

        // Act
        BigDecimal driverEarnings = financialConfig.calculateDriverEarnings(totalPrice);

        // Assert
        assertThat(driverEarnings).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void calculatePlatformCommission_WithZeroAmount_ShouldReturnZero() {
        // Arrange
        BigDecimal totalPrice = BigDecimal.ZERO;

        // Act
        BigDecimal commission = financialConfig.calculatePlatformCommission(totalPrice);

        // Assert
        assertThat(commission).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void calculateDriverEarnings_WithLargeAmount_ShouldMaintainPrecision() {
        // Arrange
        BigDecimal totalPrice = BigDecimal.valueOf(1000000.00);

        // Act
        BigDecimal driverEarnings = financialConfig.calculateDriverEarnings(totalPrice);

        // Assert
        assertThat(driverEarnings).isEqualByComparingTo(BigDecimal.valueOf(850000.00));
    }

    @Test
    void calculateEarnings_ShouldEqualTotalPriceMinusCommission() {
        // Arrange
        BigDecimal totalPrice = BigDecimal.valueOf(25000.00);

        // Act
        BigDecimal commission = financialConfig.calculatePlatformCommission(totalPrice);
        BigDecimal earnings = financialConfig.calculateDriverEarnings(totalPrice);

        // Assert
        assertThat(commission.add(earnings)).isEqualByComparingTo(totalPrice);
    }

    @Test
    void calculateDriverEarnings_WithDecimalAmount_ShouldRoundCorrectly() {
        // Arrange
        BigDecimal totalPrice = BigDecimal.valueOf(12345.67);

        // Act
        BigDecimal driverEarnings = financialConfig.calculateDriverEarnings(totalPrice);

        // Assert
        // 85% of 12345.67 = 10493.8195 -> should round to 2 decimal places
        assertThat(driverEarnings.scale()).isLessThanOrEqualTo(2);
        assertThat(driverEarnings).isGreaterThan(BigDecimal.valueOf(10493.00));
        assertThat(driverEarnings).isLessThan(BigDecimal.valueOf(10494.00));
    }
}
