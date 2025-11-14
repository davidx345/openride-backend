package com.openride.payouts.service;

import com.openride.payouts.model.entity.PayoutRequest;
import com.openride.payouts.model.entity.Settlement;
import com.openride.payouts.model.enums.PayoutStatus;
import com.openride.payouts.model.enums.SettlementStatus;
import com.openride.payouts.payment.PaymentProvider;
import com.openride.payouts.payment.PaymentProviderFactory;
import com.openride.payouts.repository.PayoutRequestRepository;
import com.openride.payouts.repository.SettlementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SettlementService.
 */
@ExtendWith(MockitoExtension.class)
class SettlementServiceTest {

    @Mock
    private SettlementRepository settlementRepository;

    @Mock
    private PayoutRequestRepository payoutRequestRepository;

    @Mock
    private PaymentProviderFactory paymentProviderFactory;

    @Mock
    private PayoutService payoutService;

    @Mock
    private PaymentProvider paymentProvider;

    @InjectMocks
    private SettlementService settlementService;

    private UUID batchId;
    private Settlement settlement;
    private List<PayoutRequest> approvedPayouts;

    @BeforeEach
    void setUp() {
        batchId = UUID.randomUUID();

        // Setup settlement
        settlement = new Settlement();
        settlement.setId(batchId);
        settlement.setTotalPayouts(3);
        settlement.setTotalAmount(BigDecimal.valueOf(60000.00));
        settlement.setSuccessfulPayouts(0);
        settlement.setFailedPayouts(0);
        settlement.setStatus(SettlementStatus.PENDING);
        settlement.setCreatedAt(LocalDateTime.now());

        // Setup approved payouts
        approvedPayouts = Arrays.asList(
                createApprovedPayout(BigDecimal.valueOf(20000.00)),
                createApprovedPayout(BigDecimal.valueOf(25000.00)),
                createApprovedPayout(BigDecimal.valueOf(15000.00))
        );

        when(paymentProviderFactory.getProvider(anyString())).thenReturn(paymentProvider);
    }

    @Test
    void createSettlementBatch_ShouldCreateBatchWithApprovedPayouts() {
        // Arrange
        when(payoutRequestRepository.findByStatus(PayoutStatus.APPROVED))
                .thenReturn(approvedPayouts);
        when(settlementRepository.save(any(Settlement.class))).thenAnswer(inv -> {
            Settlement s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        // Act
        Settlement result = settlementService.createSettlementBatch();

        // Assert
        assertThat(result).isNotNull();
        
        ArgumentCaptor<Settlement> captor = ArgumentCaptor.forClass(Settlement.class);
        verify(settlementRepository).save(captor.capture());
        
        Settlement saved = captor.getValue();
        assertThat(saved.getTotalPayouts()).isEqualTo(3);
        assertThat(saved.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(60000.00));
        assertThat(saved.getStatus()).isEqualTo(SettlementStatus.PENDING);
        assertThat(saved.getPayoutIds()).hasSize(3);
    }

    @Test
    void createSettlementBatch_WithNoApprovedPayouts_ShouldReturnNull() {
        // Arrange
        when(payoutRequestRepository.findByStatus(PayoutStatus.APPROVED))
                .thenReturn(Arrays.asList());

        // Act
        Settlement result = settlementService.createSettlementBatch();

        // Assert
        assertThat(result).isNull();
        verify(settlementRepository, never()).save(any());
    }

    @Test
    void processSettlement_WithSuccessfulTransfers_ShouldCompleteSettlement() {
        // Arrange
        when(settlementRepository.findById(batchId)).thenReturn(Optional.of(settlement));
        when(payoutRequestRepository.findAllById(anyList())).thenReturn(approvedPayouts);
        when(paymentProvider.initiateBankTransfer(any(), any(), any()))
                .thenReturn(new PaymentProvider.TransferResult(true, "PAY_123", null));
        when(settlementRepository.save(any(Settlement.class))).thenReturn(settlement);

        settlement.setPayoutIds(approvedPayouts.stream().map(PayoutRequest::getId).toList());

        // Act
        settlementService.processSettlement(batchId);

        // Assert
        ArgumentCaptor<Settlement> captor = ArgumentCaptor.forClass(Settlement.class);
        verify(settlementRepository, atLeastOnce()).save(captor.capture());
        
        Settlement saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(SettlementStatus.COMPLETED);
        assertThat(saved.getSuccessfulPayouts()).isEqualTo(3);
        assertThat(saved.getFailedPayouts()).isEqualTo(0);
        assertThat(saved.getCompletedAt()).isNotNull();

        verify(payoutService, times(3)).markAsCompleted(any(UUID.class), anyString());
        verify(payoutService, never()).markAsFailed(any(UUID.class), anyString());
    }

    @Test
    void processSettlement_WithPartialFailures_ShouldMarkAsPartiallyCompleted() {
        // Arrange
        when(settlementRepository.findById(batchId)).thenReturn(Optional.of(settlement));
        when(payoutRequestRepository.findAllById(anyList())).thenReturn(approvedPayouts);
        
        // First transfer succeeds, second fails, third succeeds
        when(paymentProvider.initiateBankTransfer(any(), any(), any()))
                .thenReturn(new PaymentProvider.TransferResult(true, "PAY_123", null))
                .thenReturn(new PaymentProvider.TransferResult(false, null, "Insufficient funds"))
                .thenReturn(new PaymentProvider.TransferResult(true, "PAY_456", null));
        
        when(settlementRepository.save(any(Settlement.class))).thenReturn(settlement);

        settlement.setPayoutIds(approvedPayouts.stream().map(PayoutRequest::getId).toList());

        // Act
        settlementService.processSettlement(batchId);

        // Assert
        ArgumentCaptor<Settlement> captor = ArgumentCaptor.forClass(Settlement.class);
        verify(settlementRepository, atLeastOnce()).save(captor.capture());
        
        Settlement saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(SettlementStatus.PARTIALLY_COMPLETED);
        assertThat(saved.getSuccessfulPayouts()).isEqualTo(2);
        assertThat(saved.getFailedPayouts()).isEqualTo(1);

        verify(payoutService, times(2)).markAsCompleted(any(UUID.class), anyString());
        verify(payoutService, times(1)).markAsFailed(any(UUID.class), anyString());
    }

    @Test
    void processSettlement_WithAllFailures_ShouldMarkAsFailed() {
        // Arrange
        when(settlementRepository.findById(batchId)).thenReturn(Optional.of(settlement));
        when(payoutRequestRepository.findAllById(anyList())).thenReturn(approvedPayouts);
        when(paymentProvider.initiateBankTransfer(any(), any(), any()))
                .thenReturn(new PaymentProvider.TransferResult(false, null, "Provider error"));
        when(settlementRepository.save(any(Settlement.class))).thenReturn(settlement);

        settlement.setPayoutIds(approvedPayouts.stream().map(PayoutRequest::getId).toList());

        // Act
        settlementService.processSettlement(batchId);

        // Assert
        ArgumentCaptor<Settlement> captor = ArgumentCaptor.forClass(Settlement.class);
        verify(settlementRepository, atLeastOnce()).save(captor.capture());
        
        Settlement saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(SettlementStatus.FAILED);
        assertThat(saved.getSuccessfulPayouts()).isEqualTo(0);
        assertThat(saved.getFailedPayouts()).isEqualTo(3);

        verify(payoutService, never()).markAsCompleted(any(UUID.class), anyString());
        verify(payoutService, times(3)).markAsFailed(any(UUID.class), anyString());
    }

    @Test
    void retrySettlement_ShouldOnlyRetryFailedPayouts() {
        // Arrange
        PayoutRequest failedPayout = createFailedPayout(BigDecimal.valueOf(20000.00));
        
        settlement.setStatus(SettlementStatus.PARTIALLY_COMPLETED);
        settlement.setSuccessfulPayouts(2);
        settlement.setFailedPayouts(1);
        settlement.setPayoutIds(Arrays.asList(failedPayout.getId()));

        when(settlementRepository.findById(batchId)).thenReturn(Optional.of(settlement));
        when(payoutRequestRepository.findAllById(anyList())).thenReturn(Arrays.asList(failedPayout));
        when(paymentProvider.initiateBankTransfer(any(), any(), any()))
                .thenReturn(new PaymentProvider.TransferResult(true, "PAY_789", null));
        when(settlementRepository.save(any(Settlement.class))).thenReturn(settlement);

        // Act
        settlementService.retrySettlement(batchId);

        // Assert
        ArgumentCaptor<Settlement> captor = ArgumentCaptor.forClass(Settlement.class);
        verify(settlementRepository, atLeastOnce()).save(captor.capture());
        
        Settlement saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(SettlementStatus.COMPLETED);
        assertThat(saved.getSuccessfulPayouts()).isEqualTo(3); // 2 + 1 retry success
        assertThat(saved.getFailedPayouts()).isEqualTo(0);

        verify(payoutService, times(1)).markAsCompleted(any(UUID.class), anyString());
    }

    @Test
    void getSettlementBatch_ShouldReturnSettlementById() {
        // Arrange
        when(settlementRepository.findById(batchId)).thenReturn(Optional.of(settlement));

        // Act
        Optional<Settlement> result = settlementService.getSettlementBatch(batchId);

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(batchId);
    }

    @Test
    void processSettlement_ShouldMarkPayoutsAsProcessing() {
        // Arrange
        when(settlementRepository.findById(batchId)).thenReturn(Optional.of(settlement));
        when(payoutRequestRepository.findAllById(anyList())).thenReturn(approvedPayouts);
        when(paymentProvider.initiateBankTransfer(any(), any(), any()))
                .thenReturn(new PaymentProvider.TransferResult(true, "PAY_123", null));
        when(settlementRepository.save(any(Settlement.class))).thenReturn(settlement);

        settlement.setPayoutIds(approvedPayouts.stream().map(PayoutRequest::getId).toList());

        // Act
        settlementService.processSettlement(batchId);

        // Assert
        // Verify each payout was marked as processing
        for (PayoutRequest payout : approvedPayouts) {
            assertThat(payout.getStatus()).isEqualTo(PayoutStatus.PROCESSING);
            assertThat(payout.getProcessedAt()).isNotNull();
        }
    }

    private PayoutRequest createApprovedPayout(BigDecimal amount) {
        PayoutRequest payout = new PayoutRequest();
        payout.setId(UUID.randomUUID());
        payout.setDriverId(UUID.randomUUID());
        payout.setWalletId(UUID.randomUUID());
        payout.setBankAccountId(UUID.randomUUID());
        payout.setAmount(amount);
        payout.setStatus(PayoutStatus.APPROVED);
        payout.setRequestedAt(LocalDateTime.now());
        payout.setReviewedAt(LocalDateTime.now());
        return payout;
    }

    private PayoutRequest createFailedPayout(BigDecimal amount) {
        PayoutRequest payout = createApprovedPayout(amount);
        payout.setStatus(PayoutStatus.FAILED);
        payout.setFailureReason("Provider error");
        return payout;
    }
}
