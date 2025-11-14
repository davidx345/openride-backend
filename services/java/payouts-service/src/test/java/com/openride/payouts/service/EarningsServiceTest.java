package com.openride.payouts.service;

import com.openride.payouts.config.FinancialConfig;
import com.openride.payouts.exception.WalletNotFoundException;
import com.openride.payouts.kafka.PayoutEventProducer;
import com.openride.payouts.model.entity.DriverWallet;
import com.openride.payouts.model.entity.EarningsLedger;
import com.openride.payouts.model.enums.LedgerEntryType;
import com.openride.payouts.model.enums.TransactionType;
import com.openride.payouts.repository.DriverWalletRepository;
import com.openride.payouts.repository.EarningsLedgerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for EarningsService.
 */
@ExtendWith(MockitoExtension.class)
class EarningsServiceTest {

    @Mock
    private DriverWalletRepository walletRepository;

    @Mock
    private EarningsLedgerRepository ledgerRepository;

    @Mock
    private FinancialConfig financialConfig;

    @Mock
    private PayoutEventProducer eventProducer;

    @InjectMocks
    private EarningsService earningsService;

    private UUID driverId;
    private UUID tripId;
    private DriverWallet wallet;
    private FinancialConfig.Commission commissionConfig;

    @BeforeEach
    void setUp() {
        driverId = UUID.randomUUID();
        tripId = UUID.randomUUID();

        // Setup wallet
        wallet = new DriverWallet();
        wallet.setId(UUID.randomUUID());
        wallet.setDriverId(driverId);
        wallet.setAvailableBalance(BigDecimal.valueOf(10000.00));
        wallet.setPendingPayout(BigDecimal.ZERO);
        wallet.setTotalEarnings(BigDecimal.valueOf(50000.00));
        wallet.setTotalPaidOut(BigDecimal.ZERO);
        wallet.setLifetimeEarnings(BigDecimal.valueOf(50000.00));
        wallet.setVersion(1L);

        // Setup commission config
        commissionConfig = new FinancialConfig.Commission();
        commissionConfig.setPlatformRate(BigDecimal.valueOf(0.15)); // 15%

        when(financialConfig.getCommission()).thenReturn(commissionConfig);
    }

    @Test
    void processEarnings_WhenWalletExists_ShouldCreditEarnings() {
        // Arrange
        BigDecimal totalPrice = BigDecimal.valueOf(10000.00);
        BigDecimal expectedDriverEarnings = BigDecimal.valueOf(8500.00); // 85% of 10000

        when(walletRepository.findByDriverIdWithLock(driverId)).thenReturn(Optional.of(wallet));
        when(walletRepository.save(any(DriverWallet.class))).thenReturn(wallet);
        when(ledgerRepository.save(any(EarningsLedger.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        earningsService.processEarnings(driverId, tripId, totalPrice);

        // Assert
        ArgumentCaptor<DriverWallet> walletCaptor = ArgumentCaptor.forClass(DriverWallet.class);
        verify(walletRepository).save(walletCaptor.capture());
        
        DriverWallet savedWallet = walletCaptor.getValue();
        assertThat(savedWallet.getAvailableBalance()).isEqualByComparingTo(BigDecimal.valueOf(18500.00));
        assertThat(savedWallet.getTotalEarnings()).isEqualByComparingTo(BigDecimal.valueOf(58500.00));
        assertThat(savedWallet.getLifetimeEarnings()).isEqualByComparingTo(BigDecimal.valueOf(58500.00));
        assertThat(savedWallet.getLastEarningAt()).isNotNull();

        // Verify ledger entry created
        ArgumentCaptor<EarningsLedger> ledgerCaptor = ArgumentCaptor.forClass(EarningsLedger.class);
        verify(ledgerRepository).save(ledgerCaptor.capture());
        
        EarningsLedger ledgerEntry = ledgerCaptor.getValue();
        assertThat(ledgerEntry.getDriverId()).isEqualTo(driverId);
        assertThat(ledgerEntry.getAmount()).isEqualByComparingTo(expectedDriverEarnings);
        assertThat(ledgerEntry.getEntryType()).isEqualTo(LedgerEntryType.CREDIT);
        assertThat(ledgerEntry.getTransactionType()).isEqualTo(TransactionType.EARNING);
        assertThat(ledgerEntry.getReferenceId()).isEqualTo(tripId);
    }

    @Test
    void processEarnings_WhenWalletDoesNotExist_ShouldCreateWalletAndCreditEarnings() {
        // Arrange
        BigDecimal totalPrice = BigDecimal.valueOf(5000.00);
        BigDecimal expectedDriverEarnings = BigDecimal.valueOf(4250.00); // 85% of 5000

        when(walletRepository.findByDriverIdWithLock(driverId)).thenReturn(Optional.empty());
        when(walletRepository.save(any(DriverWallet.class))).thenAnswer(invocation -> {
            DriverWallet newWallet = invocation.getArgument(0);
            newWallet.setId(UUID.randomUUID());
            return newWallet;
        });
        when(ledgerRepository.save(any(EarningsLedger.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        earningsService.processEarnings(driverId, tripId, totalPrice);

        // Assert
        ArgumentCaptor<DriverWallet> walletCaptor = ArgumentCaptor.forClass(DriverWallet.class);
        verify(walletRepository, times(2)).save(walletCaptor.capture()); // Once for creation, once for update
        
        DriverWallet createdWallet = walletCaptor.getAllValues().get(0);
        assertThat(createdWallet.getDriverId()).isEqualTo(driverId);
        assertThat(createdWallet.getAvailableBalance()).isEqualByComparingTo(BigDecimal.ZERO);

        DriverWallet updatedWallet = walletCaptor.getAllValues().get(1);
        assertThat(updatedWallet.getAvailableBalance()).isEqualByComparingTo(expectedDriverEarnings);
        assertThat(updatedWallet.getTotalEarnings()).isEqualByComparingTo(expectedDriverEarnings);
        assertThat(updatedWallet.getLifetimeEarnings()).isEqualByComparingTo(expectedDriverEarnings);
    }

    @Test
    void processEarnings_ShouldCalculateCorrectCommission() {
        // Arrange
        BigDecimal totalPrice = BigDecimal.valueOf(20000.00);
        BigDecimal expectedPlatformCommission = BigDecimal.valueOf(3000.00); // 15% of 20000
        BigDecimal expectedDriverEarnings = BigDecimal.valueOf(17000.00); // 85% of 20000

        when(walletRepository.findByDriverIdWithLock(driverId)).thenReturn(Optional.of(wallet));
        when(walletRepository.save(any(DriverWallet.class))).thenReturn(wallet);
        when(ledgerRepository.save(any(EarningsLedger.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        earningsService.processEarnings(driverId, tripId, totalPrice);

        // Assert
        ArgumentCaptor<EarningsLedger> ledgerCaptor = ArgumentCaptor.forClass(EarningsLedger.class);
        verify(ledgerRepository).save(ledgerCaptor.capture());
        
        EarningsLedger ledgerEntry = ledgerCaptor.getValue();
        assertThat(ledgerEntry.getAmount()).isEqualByComparingTo(expectedDriverEarnings);
        
        // Verify commission calculation through wallet balance change
        ArgumentCaptor<DriverWallet> walletCaptor = ArgumentCaptor.forClass(DriverWallet.class);
        verify(walletRepository).save(walletCaptor.capture());
        
        BigDecimal balanceIncrease = walletCaptor.getValue().getAvailableBalance()
                .subtract(BigDecimal.valueOf(10000.00)); // Original balance
        assertThat(balanceIncrease).isEqualByComparingTo(expectedDriverEarnings);
    }

    @Test
    void processEarnings_WithZeroAmount_ShouldNotCreditWallet() {
        // Arrange
        BigDecimal totalPrice = BigDecimal.ZERO;

        when(walletRepository.findByDriverIdWithLock(driverId)).thenReturn(Optional.of(wallet));

        // Act
        earningsService.processEarnings(driverId, tripId, totalPrice);

        // Assert
        // Wallet should be saved but balance should not change
        ArgumentCaptor<DriverWallet> walletCaptor = ArgumentCaptor.forClass(DriverWallet.class);
        verify(walletRepository).save(walletCaptor.capture());
        
        DriverWallet savedWallet = walletCaptor.getValue();
        assertThat(savedWallet.getAvailableBalance()).isEqualByComparingTo(BigDecimal.valueOf(10000.00));
    }

    @Test
    void processEarnings_ShouldSetBalanceAfterInLedger() {
        // Arrange
        BigDecimal totalPrice = BigDecimal.valueOf(10000.00);

        when(walletRepository.findByDriverIdWithLock(driverId)).thenReturn(Optional.of(wallet));
        when(walletRepository.save(any(DriverWallet.class))).thenReturn(wallet);
        when(ledgerRepository.save(any(EarningsLedger.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        earningsService.processEarnings(driverId, tripId, totalPrice);

        // Assert
        ArgumentCaptor<EarningsLedger> ledgerCaptor = ArgumentCaptor.forClass(EarningsLedger.class);
        verify(ledgerRepository).save(ledgerCaptor.capture());
        
        EarningsLedger ledgerEntry = ledgerCaptor.getValue();
        assertThat(ledgerEntry.getBalanceAfter()).isNotNull();
        assertThat(ledgerEntry.getBalanceAfter()).isEqualByComparingTo(BigDecimal.valueOf(18500.00));
    }

    @Test
    void processEarnings_ShouldSetCorrectMetadataInLedger() {
        // Arrange
        BigDecimal totalPrice = BigDecimal.valueOf(10000.00);

        when(walletRepository.findByDriverIdWithLock(driverId)).thenReturn(Optional.of(wallet));
        when(walletRepository.save(any(DriverWallet.class))).thenReturn(wallet);
        when(ledgerRepository.save(any(EarningsLedger.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        earningsService.processEarnings(driverId, tripId, totalPrice);

        // Assert
        ArgumentCaptor<EarningsLedger> ledgerCaptor = ArgumentCaptor.forClass(EarningsLedger.class);
        verify(ledgerRepository).save(ledgerCaptor.capture());
        
        EarningsLedger ledgerEntry = ledgerCaptor.getValue();
        assertThat(ledgerEntry.getDescription()).contains("Trip earnings");
        assertThat(ledgerEntry.getReferenceId()).isEqualTo(tripId);
        assertThat(ledgerEntry.getReferenceType()).isEqualTo("TRIP");
    }

    @Test
    void processEarnings_WithDifferentCommissionRate_ShouldCalculateCorrectly() {
        // Arrange
        BigDecimal totalPrice = BigDecimal.valueOf(10000.00);
        FinancialConfig.Commission customCommission = new FinancialConfig.Commission();
        customCommission.setPlatformRate(BigDecimal.valueOf(0.20)); // 20% commission

        when(financialConfig.getCommission()).thenReturn(customCommission);
        when(walletRepository.findByDriverIdWithLock(driverId)).thenReturn(Optional.of(wallet));
        when(walletRepository.save(any(DriverWallet.class))).thenReturn(wallet);
        when(ledgerRepository.save(any(EarningsLedger.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        earningsService.processEarnings(driverId, tripId, totalPrice);

        // Assert
        ArgumentCaptor<EarningsLedger> ledgerCaptor = ArgumentCaptor.forClass(EarningsLedger.class);
        verify(ledgerRepository).save(ledgerCaptor.capture());
        
        EarningsLedger ledgerEntry = ledgerCaptor.getValue();
        assertThat(ledgerEntry.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(8000.00)); // 80% of 10000
    }

    @Test
    void processEarnings_ShouldUseWalletIdInLedger() {
        // Arrange
        BigDecimal totalPrice = BigDecimal.valueOf(10000.00);

        when(walletRepository.findByDriverIdWithLock(driverId)).thenReturn(Optional.of(wallet));
        when(walletRepository.save(any(DriverWallet.class))).thenReturn(wallet);
        when(ledgerRepository.save(any(EarningsLedger.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        earningsService.processEarnings(driverId, tripId, totalPrice);

        // Assert
        ArgumentCaptor<EarningsLedger> ledgerCaptor = ArgumentCaptor.forClass(EarningsLedger.class);
        verify(ledgerRepository).save(ledgerCaptor.capture());
        
        EarningsLedger ledgerEntry = ledgerCaptor.getValue();
        assertThat(ledgerEntry.getWalletId()).isEqualTo(wallet.getId());
    }
}
