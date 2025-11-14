package com.openride.payouts.service;

import com.openride.payouts.dto.EarningsSummaryResponse;
import com.openride.payouts.dto.LedgerEntryResponse;
import com.openride.payouts.dto.WalletResponse;
import com.openride.payouts.exception.WalletNotFoundException;
import com.openride.payouts.model.entity.DriverWallet;
import com.openride.payouts.model.entity.EarningsLedger;
import com.openride.payouts.model.entity.PayoutRequest;
import com.openride.payouts.model.enums.LedgerEntryType;
import com.openride.payouts.model.enums.TransactionType;
import com.openride.payouts.repository.DriverWalletRepository;
import com.openride.payouts.repository.EarningsLedgerRepository;
import com.openride.payouts.repository.PayoutRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for WalletService.
 */
@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock
    private DriverWalletRepository walletRepository;

    @Mock
    private EarningsLedgerRepository ledgerRepository;

    @Mock
    private PayoutRequestRepository payoutRequestRepository;

    @InjectMocks
    private WalletService walletService;

    private UUID driverId;
    private DriverWallet wallet;

    @BeforeEach
    void setUp() {
        driverId = UUID.randomUUID();

        wallet = new DriverWallet();
        wallet.setId(UUID.randomUUID());
        wallet.setDriverId(driverId);
        wallet.setAvailableBalance(BigDecimal.valueOf(25000.00));
        wallet.setPendingPayout(BigDecimal.valueOf(5000.00));
        wallet.setTotalEarnings(BigDecimal.valueOf(100000.00));
        wallet.setTotalPaidOut(BigDecimal.valueOf(75000.00));
        wallet.setLifetimeEarnings(BigDecimal.valueOf(100000.00));
        wallet.setLastEarningAt(LocalDateTime.now().minusDays(1));
        wallet.setCreatedAt(LocalDateTime.now().minusMonths(6));
    }

    @Test
    void getWallet_WhenWalletExists_ShouldReturnWalletResponse() {
        // Arrange
        when(walletRepository.findByDriverId(driverId)).thenReturn(Optional.of(wallet));

        // Act
        WalletResponse response = walletService.getWallet(driverId);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(wallet.getId());
        assertThat(response.getDriverId()).isEqualTo(driverId);
        assertThat(response.getAvailableBalance()).isEqualByComparingTo(BigDecimal.valueOf(25000.00));
        assertThat(response.getPendingPayout()).isEqualByComparingTo(BigDecimal.valueOf(5000.00));
        assertThat(response.getTotalEarnings()).isEqualByComparingTo(BigDecimal.valueOf(100000.00));
        assertThat(response.getTotalPaidOut()).isEqualByComparingTo(BigDecimal.valueOf(75000.00));
        assertThat(response.getLifetimeEarnings()).isEqualByComparingTo(BigDecimal.valueOf(100000.00));
        
        verify(walletRepository).findByDriverId(driverId);
    }

    @Test
    void getWallet_WhenWalletDoesNotExist_ShouldThrowException() {
        // Arrange
        when(walletRepository.findByDriverId(driverId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> walletService.getWallet(driverId))
                .isInstanceOf(WalletNotFoundException.class)
                .hasMessageContaining(driverId.toString());
        
        verify(walletRepository).findByDriverId(driverId);
    }

    @Test
    void getOrCreateWallet_WhenWalletExists_ShouldReturnExistingWallet() {
        // Arrange
        when(walletRepository.findByDriverId(driverId)).thenReturn(Optional.of(wallet));

        // Act
        WalletResponse response = walletService.getOrCreateWallet(driverId);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(wallet.getId());
        assertThat(response.getDriverId()).isEqualTo(driverId);
        
        verify(walletRepository).findByDriverId(driverId);
        verify(walletRepository, never()).save(any());
    }

    @Test
    void getOrCreateWallet_WhenWalletDoesNotExist_ShouldCreateNewWallet() {
        // Arrange
        when(walletRepository.findByDriverId(driverId)).thenReturn(Optional.empty());
        when(walletRepository.save(any(DriverWallet.class))).thenAnswer(invocation -> {
            DriverWallet newWallet = invocation.getArgument(0);
            newWallet.setId(UUID.randomUUID());
            return newWallet;
        });

        // Act
        WalletResponse response = walletService.getOrCreateWallet(driverId);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getDriverId()).isEqualTo(driverId);
        assertThat(response.getAvailableBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.getPendingPayout()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.getTotalEarnings()).isEqualByComparingTo(BigDecimal.ZERO);
        
        verify(walletRepository).findByDriverId(driverId);
        verify(walletRepository).save(any(DriverWallet.class));
    }

    @Test
    void getTransactionHistory_ShouldReturnPagedLedgerEntries() {
        // Arrange
        EarningsLedger entry1 = createLedgerEntry(TransactionType.EARNING, BigDecimal.valueOf(8500.00));
        EarningsLedger entry2 = createLedgerEntry(TransactionType.PAYOUT, BigDecimal.valueOf(10000.00));
        
        Pageable pageable = PageRequest.of(0, 20);
        Page<EarningsLedger> ledgerPage = new PageImpl<>(Arrays.asList(entry1, entry2), pageable, 2);

        when(walletRepository.findByDriverId(driverId)).thenReturn(Optional.of(wallet));
        when(ledgerRepository.findByDriverId(driverId, pageable)).thenReturn(ledgerPage);

        // Act
        Page<LedgerEntryResponse> result = walletService.getTransactionHistory(driverId, pageable);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent()).hasSize(2);
        
        LedgerEntryResponse firstEntry = result.getContent().get(0);
        assertThat(firstEntry.getTransactionType()).isEqualTo(TransactionType.EARNING);
        assertThat(firstEntry.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(8500.00));
        
        verify(walletRepository).findByDriverId(driverId);
        verify(ledgerRepository).findByDriverId(driverId, pageable);
    }

    @Test
    void getTransactionHistory_WhenWalletDoesNotExist_ShouldThrowException() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 20);
        when(walletRepository.findByDriverId(driverId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> walletService.getTransactionHistory(driverId, pageable))
                .isInstanceOf(WalletNotFoundException.class);
        
        verify(walletRepository).findByDriverId(driverId);
        verify(ledgerRepository, never()).findByDriverId(any(), any());
    }

    @Test
    void getEarningsSummary_ShouldReturnCompleteEarningsSummary() {
        // Arrange
        LocalDateTime lastPayoutAt = LocalDateTime.now().minusDays(7);
        LocalDateTime lastEarningAt = LocalDateTime.now().minusHours(2);
        
        PayoutRequest lastPayout = new PayoutRequest();
        lastPayout.setCreatedAt(lastPayoutAt);
        
        EarningsLedger lastEarning = createLedgerEntry(TransactionType.EARNING, BigDecimal.valueOf(8500.00));
        lastEarning.setCreatedAt(lastEarningAt);

        when(walletRepository.findByDriverId(driverId)).thenReturn(Optional.of(wallet));
        when(payoutRequestRepository.findTopByDriverIdOrderByCreatedAtDesc(driverId))
                .thenReturn(Optional.of(lastPayout));
        when(ledgerRepository.findTopByDriverIdAndTransactionTypeOrderByCreatedAtDesc(driverId, TransactionType.EARNING))
                .thenReturn(Optional.of(lastEarning));
        when(ledgerRepository.countByDriverIdAndTransactionType(driverId, TransactionType.EARNING))
                .thenReturn(125L);

        // Act
        EarningsSummaryResponse summary = walletService.getEarningsSummary(driverId);

        // Assert
        assertThat(summary).isNotNull();
        assertThat(summary.getAvailableBalance()).isEqualByComparingTo(BigDecimal.valueOf(25000.00));
        assertThat(summary.getPendingPayout()).isEqualByComparingTo(BigDecimal.valueOf(5000.00));
        assertThat(summary.getTotalEarnings()).isEqualByComparingTo(BigDecimal.valueOf(100000.00));
        assertThat(summary.getTotalPaidOut()).isEqualByComparingTo(BigDecimal.valueOf(75000.00));
        assertThat(summary.getLifetimeEarnings()).isEqualByComparingTo(BigDecimal.valueOf(100000.00));
        assertThat(summary.getTotalTrips()).isEqualTo(125);
        assertThat(summary.getLastPayoutAt()).isEqualTo(lastPayoutAt);
        assertThat(summary.getLastEarningAt()).isEqualTo(lastEarningAt);
    }

    @Test
    void getEarningsSummary_WhenNoPayoutsExist_ShouldReturnSummaryWithNullLastPayout() {
        // Arrange
        when(walletRepository.findByDriverId(driverId)).thenReturn(Optional.of(wallet));
        when(payoutRequestRepository.findTopByDriverIdOrderByCreatedAtDesc(driverId))
                .thenReturn(Optional.empty());
        when(ledgerRepository.findTopByDriverIdAndTransactionTypeOrderByCreatedAtDesc(driverId, TransactionType.EARNING))
                .thenReturn(Optional.empty());
        when(ledgerRepository.countByDriverIdAndTransactionType(driverId, TransactionType.EARNING))
                .thenReturn(50L);

        // Act
        EarningsSummaryResponse summary = walletService.getEarningsSummary(driverId);

        // Assert
        assertThat(summary).isNotNull();
        assertThat(summary.getLastPayoutAt()).isNull();
        assertThat(summary.getLastEarningAt()).isNull();
        assertThat(summary.getTotalTrips()).isEqualTo(50);
    }

    @Test
    void getAvailableBalance_ShouldReturnCorrectBalance() {
        // Arrange
        when(walletRepository.findByDriverId(driverId)).thenReturn(Optional.of(wallet));

        // Act
        BigDecimal balance = walletService.getAvailableBalance(driverId);

        // Assert
        assertThat(balance).isEqualByComparingTo(BigDecimal.valueOf(25000.00));
        verify(walletRepository).findByDriverId(driverId);
    }

    @Test
    void getAvailableBalance_WhenWalletDoesNotExist_ShouldThrowException() {
        // Arrange
        when(walletRepository.findByDriverId(driverId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> walletService.getAvailableBalance(driverId))
                .isInstanceOf(WalletNotFoundException.class);
    }

    private EarningsLedger createLedgerEntry(TransactionType type, BigDecimal amount) {
        EarningsLedger entry = new EarningsLedger();
        entry.setId(UUID.randomUUID());
        entry.setWalletId(wallet.getId());
        entry.setDriverId(driverId);
        entry.setAmount(amount);
        entry.setBalanceAfter(BigDecimal.valueOf(25000.00));
        entry.setEntryType(type == TransactionType.EARNING ? LedgerEntryType.CREDIT : LedgerEntryType.DEBIT);
        entry.setTransactionType(type);
        entry.setReferenceId(UUID.randomUUID());
        entry.setReferenceType("TRIP");
        entry.setDescription("Test transaction");
        entry.setCreatedAt(LocalDateTime.now());
        return entry;
    }
}
