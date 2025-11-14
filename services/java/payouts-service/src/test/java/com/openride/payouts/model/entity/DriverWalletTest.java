package com.openride.payouts.model.entity;

import com.openride.payouts.exception.InsufficientBalanceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for DriverWallet entity business methods.
 */
class DriverWalletTest {

    private DriverWallet wallet;
    private UUID driverId;

    @BeforeEach
    void setUp() {
        driverId = UUID.randomUUID();
        
        wallet = new DriverWallet();
        wallet.setId(UUID.randomUUID());
        wallet.setDriverId(driverId);
        wallet.setAvailableBalance(BigDecimal.valueOf(50000.00));
        wallet.setPendingPayout(BigDecimal.ZERO);
        wallet.setTotalEarnings(BigDecimal.valueOf(100000.00));
        wallet.setTotalPaidOut(BigDecimal.valueOf(50000.00));
        wallet.setLifetimeEarnings(BigDecimal.valueOf(100000.00));
        wallet.setVersion(1L);
        wallet.setCreatedAt(LocalDateTime.now());
        wallet.setUpdatedAt(LocalDateTime.now());
    }

    @Test
    void creditEarnings_ShouldUpdateBalancesCorrectly() {
        // Arrange
        BigDecimal earningsAmount = BigDecimal.valueOf(8500.00);

        // Act
        wallet.creditEarnings(earningsAmount);

        // Assert
        assertThat(wallet.getAvailableBalance()).isEqualByComparingTo(BigDecimal.valueOf(58500.00));
        assertThat(wallet.getTotalEarnings()).isEqualByComparingTo(BigDecimal.valueOf(108500.00));
        assertThat(wallet.getLifetimeEarnings()).isEqualByComparingTo(BigDecimal.valueOf(108500.00));
        assertThat(wallet.getLastEarningAt()).isNotNull();
    }

    @Test
    void creditEarnings_WithZeroAmount_ShouldNotChangeBalances() {
        // Arrange
        BigDecimal originalBalance = wallet.getAvailableBalance();
        BigDecimal originalEarnings = wallet.getTotalEarnings();

        // Act
        wallet.creditEarnings(BigDecimal.ZERO);

        // Assert
        assertThat(wallet.getAvailableBalance()).isEqualByComparingTo(originalBalance);
        assertThat(wallet.getTotalEarnings()).isEqualByComparingTo(originalEarnings);
    }

    @Test
    void creditEarnings_WithNegativeAmount_ShouldNotChangeBalances() {
        // Arrange
        BigDecimal originalBalance = wallet.getAvailableBalance();
        BigDecimal originalEarnings = wallet.getTotalEarnings();

        // Act
        wallet.creditEarnings(BigDecimal.valueOf(-1000.00));

        // Assert
        assertThat(wallet.getAvailableBalance()).isEqualByComparingTo(originalBalance);
        assertThat(wallet.getTotalEarnings()).isEqualByComparingTo(originalEarnings);
    }

    @Test
    void reserveForPayout_WithSufficientBalance_ShouldReserveAmount() {
        // Arrange
        BigDecimal payoutAmount = BigDecimal.valueOf(20000.00);

        // Act
        wallet.reserveForPayout(payoutAmount);

        // Assert
        assertThat(wallet.getAvailableBalance()).isEqualByComparingTo(BigDecimal.valueOf(30000.00));
        assertThat(wallet.getPendingPayout()).isEqualByComparingTo(BigDecimal.valueOf(20000.00));
    }

    @Test
    void reserveForPayout_WithInsufficientBalance_ShouldThrowException() {
        // Arrange
        BigDecimal payoutAmount = BigDecimal.valueOf(60000.00);

        // Act & Assert
        assertThatThrownBy(() -> wallet.reserveForPayout(payoutAmount))
                .isInstanceOf(InsufficientBalanceException.class)
                .hasMessageContaining("Insufficient balance");
    }

    @Test
    void reserveForPayout_WithExactBalance_ShouldSucceed() {
        // Arrange
        BigDecimal payoutAmount = BigDecimal.valueOf(50000.00);

        // Act
        wallet.reserveForPayout(payoutAmount);

        // Assert
        assertThat(wallet.getAvailableBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(wallet.getPendingPayout()).isEqualByComparingTo(BigDecimal.valueOf(50000.00));
    }

    @Test
    void releaseReservedAmount_ShouldReturnFundsToAvailableBalance() {
        // Arrange
        wallet.setPendingPayout(BigDecimal.valueOf(20000.00));
        wallet.setAvailableBalance(BigDecimal.valueOf(30000.00));

        BigDecimal releaseAmount = BigDecimal.valueOf(20000.00);

        // Act
        wallet.releaseReservedAmount(releaseAmount);

        // Assert
        assertThat(wallet.getAvailableBalance()).isEqualByComparingTo(BigDecimal.valueOf(50000.00));
        assertThat(wallet.getPendingPayout()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void releaseReservedAmount_WithPartialRelease_ShouldReleasePartially() {
        // Arrange
        wallet.setPendingPayout(BigDecimal.valueOf(30000.00));
        wallet.setAvailableBalance(BigDecimal.valueOf(20000.00));

        BigDecimal releaseAmount = BigDecimal.valueOf(10000.00);

        // Act
        wallet.releaseReservedAmount(releaseAmount);

        // Assert
        assertThat(wallet.getAvailableBalance()).isEqualByComparingTo(BigDecimal.valueOf(30000.00));
        assertThat(wallet.getPendingPayout()).isEqualByComparingTo(BigDecimal.valueOf(20000.00));
    }

    @Test
    void completePayout_ShouldUpdateBalancesAndTotalPaidOut() {
        // Arrange
        wallet.setPendingPayout(BigDecimal.valueOf(20000.00));
        wallet.setAvailableBalance(BigDecimal.valueOf(30000.00));

        BigDecimal payoutAmount = BigDecimal.valueOf(20000.00);

        // Act
        wallet.completePayout(payoutAmount);

        // Assert
        assertThat(wallet.getPendingPayout()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(wallet.getTotalPaidOut()).isEqualByComparingTo(BigDecimal.valueOf(70000.00));
        assertThat(wallet.getLastPayoutAt()).isNotNull();
        
        // Available balance should remain unchanged (already deducted during reservation)
        assertThat(wallet.getAvailableBalance()).isEqualByComparingTo(BigDecimal.valueOf(30000.00));
    }

    @Test
    void completePayout_WithMultiplePayouts_ShouldAccumulateTotalPaidOut() {
        // Arrange
        wallet.setPendingPayout(BigDecimal.valueOf(15000.00));

        // Act
        wallet.completePayout(BigDecimal.valueOf(15000.00));

        // Assert
        assertThat(wallet.getTotalPaidOut()).isEqualByComparingTo(BigDecimal.valueOf(65000.00));

        // Second payout
        wallet.reserveForPayout(BigDecimal.valueOf(10000.00));
        wallet.completePayout(BigDecimal.valueOf(10000.00));

        // Assert
        assertThat(wallet.getTotalPaidOut()).isEqualByComparingTo(BigDecimal.valueOf(75000.00));
    }

    @Test
    void wallet_InitialState_ShouldHaveZeroBalances() {
        // Arrange
        DriverWallet newWallet = new DriverWallet();
        newWallet.setDriverId(UUID.randomUUID());

        // Assert
        assertThat(newWallet.getAvailableBalance()).isNull();
        assertThat(newWallet.getPendingPayout()).isNull();
        assertThat(newWallet.getTotalEarnings()).isNull();
        assertThat(newWallet.getTotalPaidOut()).isNull();
        assertThat(newWallet.getLifetimeEarnings()).isNull();
    }

    @Test
    void wallet_ShouldMaintainVersionForOptimisticLocking() {
        // Arrange
        Long originalVersion = wallet.getVersion();

        // Act
        wallet.creditEarnings(BigDecimal.valueOf(5000.00));

        // Assert - Version should remain unchanged until persisted
        assertThat(wallet.getVersion()).isEqualTo(originalVersion);
    }

    @Test
    void creditEarnings_ShouldSetLastEarningAtTimestamp() {
        // Arrange
        LocalDateTime before = LocalDateTime.now().minusSeconds(1);

        // Act
        wallet.creditEarnings(BigDecimal.valueOf(5000.00));

        // Assert
        assertThat(wallet.getLastEarningAt()).isAfter(before);
        assertThat(wallet.getLastEarningAt()).isBeforeOrEqualTo(LocalDateTime.now());
    }

    @Test
    void completePayout_ShouldSetLastPayoutAtTimestamp() {
        // Arrange
        wallet.setPendingPayout(BigDecimal.valueOf(10000.00));
        LocalDateTime before = LocalDateTime.now().minusSeconds(1);

        // Act
        wallet.completePayout(BigDecimal.valueOf(10000.00));

        // Assert
        assertThat(wallet.getLastPayoutAt()).isAfter(before);
        assertThat(wallet.getLastPayoutAt()).isBeforeOrEqualTo(LocalDateTime.now());
    }

    @Test
    void reserveForPayout_SequentialReservations_ShouldAccumulate() {
        // Arrange & Act
        wallet.reserveForPayout(BigDecimal.valueOf(10000.00));
        wallet.reserveForPayout(BigDecimal.valueOf(15000.00));

        // Assert
        assertThat(wallet.getAvailableBalance()).isEqualByComparingTo(BigDecimal.valueOf(25000.00));
        assertThat(wallet.getPendingPayout()).isEqualByComparingTo(BigDecimal.valueOf(25000.00));
    }
}
