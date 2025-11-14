package com.openride.payouts.service;

import com.openride.payouts.config.FinancialConfig;
import com.openride.payouts.dto.PayoutRequestDto;
import com.openride.payouts.dto.PayoutResponse;
import com.openride.payouts.dto.PayoutReviewRequest;
import com.openride.payouts.exception.*;
import com.openride.payouts.kafka.PayoutEventProducer;
import com.openride.payouts.model.entity.BankAccount;
import com.openride.payouts.model.entity.DriverWallet;
import com.openride.payouts.model.entity.PayoutRequest;
import com.openride.payouts.model.enums.PayoutStatus;
import com.openride.payouts.repository.BankAccountRepository;
import com.openride.payouts.repository.DriverWalletRepository;
import com.openride.payouts.repository.PayoutRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.*;

/**
 * Unit tests for PayoutService.
 */
@ExtendWith(MockitoExtension.class)
class PayoutServiceTest {

    @Mock
    private PayoutRequestRepository payoutRequestRepository;

    @Mock
    private DriverWalletRepository walletRepository;

    @Mock
    private BankAccountRepository bankAccountRepository;

    @Mock
    private FinancialConfig financialConfig;

    @Mock
    private PayoutEventProducer payoutEventProducer;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private PayoutService payoutService;

    private UUID driverId;
    private UUID adminId;
    private DriverWallet wallet;
    private BankAccount bankAccount;
    private FinancialConfig.Payout payoutConfig;

    @BeforeEach
    void setUp() {
        driverId = UUID.randomUUID();
        adminId = UUID.randomUUID();

        // Setup wallet
        wallet = new DriverWallet();
        wallet.setId(UUID.randomUUID());
        wallet.setDriverId(driverId);
        wallet.setAvailableBalance(BigDecimal.valueOf(50000.00));
        wallet.setPendingPayout(BigDecimal.ZERO);
        wallet.setTotalEarnings(BigDecimal.valueOf(100000.00));
        wallet.setTotalPaidOut(BigDecimal.valueOf(50000.00));
        wallet.setVersion(1L);

        // Setup bank account
        bankAccount = new BankAccount();
        bankAccount.setId(UUID.randomUUID());
        bankAccount.setDriverId(driverId);
        bankAccount.setAccountNumber("0123456789");
        bankAccount.setBankCode("058");
        bankAccount.setBankName("GTBank");
        bankAccount.setAccountName("John Doe");
        bankAccount.setIsVerified(true);
        bankAccount.setIsPrimary(true);

        // Setup payout config
        payoutConfig = new FinancialConfig.Payout();
        payoutConfig.setMinimumAmount(BigDecimal.valueOf(5000.00));

        when(financialConfig.getPayout()).thenReturn(payoutConfig);
    }

    @Test
    void requestPayout_WithValidRequest_ShouldCreatePendingPayout() {
        // Arrange
        PayoutRequestDto request = new PayoutRequestDto();
        request.setAmount(BigDecimal.valueOf(20000.00));
        request.setBankAccountId(bankAccount.getId());

        when(walletRepository.findByDriverIdWithLock(driverId)).thenReturn(Optional.of(wallet));
        when(bankAccountRepository.findById(bankAccount.getId())).thenReturn(Optional.of(bankAccount));
        when(payoutRequestRepository.hasPendingPayout(driverId)).thenReturn(false);
        when(payoutRequestRepository.save(any(PayoutRequest.class))).thenAnswer(inv -> {
            PayoutRequest payout = inv.getArgument(0);
            payout.setId(UUID.randomUUID());
            return payout;
        });
        when(walletRepository.save(any(DriverWallet.class))).thenReturn(wallet);

        // Act
        PayoutResponse response = payoutService.requestPayout(driverId, request);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getDriverId()).isEqualTo(driverId);
        assertThat(response.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(20000.00));
        assertThat(response.getStatus()).isEqualTo(PayoutStatus.PENDING);

        // Verify wallet was updated
        ArgumentCaptor<DriverWallet> walletCaptor = ArgumentCaptor.forClass(DriverWallet.class);
        verify(walletRepository).save(walletCaptor.capture());
        DriverWallet savedWallet = walletCaptor.getValue();
        assertThat(savedWallet.getAvailableBalance()).isEqualByComparingTo(BigDecimal.valueOf(30000.00));
        assertThat(savedWallet.getPendingPayout()).isEqualByComparingTo(BigDecimal.valueOf(20000.00));

        // Verify event was published
        verify(payoutEventProducer).publishPayoutRequested(any(PayoutRequest.class));
        verify(auditService).logAuditEntry(eq("PAYOUT_REQUEST"), any(), eq("REQUEST_PAYOUT"), eq(driverId), any(), any());
    }

    @Test
    void requestPayout_WithAmountBelowMinimum_ShouldThrowException() {
        // Arrange
        PayoutRequestDto request = new PayoutRequestDto();
        request.setAmount(BigDecimal.valueOf(3000.00)); // Below 5000 minimum
        request.setBankAccountId(bankAccount.getId());

        // Act & Assert
        assertThatThrownBy(() -> payoutService.requestPayout(driverId, request))
                .isInstanceOf(MinimumPayoutAmountException.class);

        verify(walletRepository, never()).save(any());
        verify(payoutRequestRepository, never()).save(any());
    }

    @Test
    void requestPayout_WithInsufficientBalance_ShouldThrowException() {
        // Arrange
        wallet.setAvailableBalance(BigDecimal.valueOf(3000.00));
        
        PayoutRequestDto request = new PayoutRequestDto();
        request.setAmount(BigDecimal.valueOf(10000.00));
        request.setBankAccountId(bankAccount.getId());

        when(walletRepository.findByDriverIdWithLock(driverId)).thenReturn(Optional.of(wallet));

        // Act & Assert
        assertThatThrownBy(() -> payoutService.requestPayout(driverId, request))
                .isInstanceOf(InsufficientBalanceException.class);

        verify(payoutRequestRepository, never()).save(any());
    }

    @Test
    void requestPayout_WithUnverifiedBankAccount_ShouldThrowException() {
        // Arrange
        bankAccount.setIsVerified(false);
        
        PayoutRequestDto request = new PayoutRequestDto();
        request.setAmount(BigDecimal.valueOf(10000.00));
        request.setBankAccountId(bankAccount.getId());

        when(walletRepository.findByDriverIdWithLock(driverId)).thenReturn(Optional.of(wallet));
        when(bankAccountRepository.findById(bankAccount.getId())).thenReturn(Optional.of(bankAccount));
        when(payoutRequestRepository.hasPendingPayout(driverId)).thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> payoutService.requestPayout(driverId, request))
                .isInstanceOf(BankAccountNotVerifiedException.class);

        verify(payoutRequestRepository, never()).save(any());
    }

    @Test
    void requestPayout_WithExistingPendingPayout_ShouldThrowException() {
        // Arrange
        PayoutRequestDto request = new PayoutRequestDto();
        request.setAmount(BigDecimal.valueOf(10000.00));
        request.setBankAccountId(bankAccount.getId());

        when(walletRepository.findByDriverIdWithLock(driverId)).thenReturn(Optional.of(wallet));
        when(bankAccountRepository.findById(bankAccount.getId())).thenReturn(Optional.of(bankAccount));
        when(payoutRequestRepository.hasPendingPayout(driverId)).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> payoutService.requestPayout(driverId, request))
                .isInstanceOf(PendingPayoutExistsException.class);

        verify(payoutRequestRepository, never()).save(any());
    }

    @Test
    void approvePayoutRequest_ShouldChangeStatusToApproved() {
        // Arrange
        PayoutRequest payout = createPendingPayout();
        PayoutReviewRequest reviewRequest = new PayoutReviewRequest();
        reviewRequest.setNotes("Looks good");

        when(payoutRequestRepository.findById(payout.getId())).thenReturn(Optional.of(payout));
        when(payoutRequestRepository.save(any(PayoutRequest.class))).thenReturn(payout);
        when(bankAccountRepository.findById(bankAccount.getId())).thenReturn(Optional.of(bankAccount));

        // Act
        PayoutResponse response = payoutService.approvePayoutRequest(payout.getId(), adminId, reviewRequest);

        // Assert
        assertThat(response.getStatus()).isEqualTo(PayoutStatus.APPROVED);
        
        ArgumentCaptor<PayoutRequest> captor = ArgumentCaptor.forClass(PayoutRequest.class);
        verify(payoutRequestRepository).save(captor.capture());
        
        PayoutRequest saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(PayoutStatus.APPROVED);
        assertThat(saved.getReviewedBy()).isEqualTo(adminId);
        assertThat(saved.getReviewNotes()).isEqualTo("Looks good");
        assertThat(saved.getReviewedAt()).isNotNull();

        verify(payoutEventProducer).publishPayoutApproved(any(PayoutRequest.class));
    }

    @Test
    void rejectPayoutRequest_ShouldReleaseReservedFunds() {
        // Arrange
        PayoutRequest payout = createPendingPayout();
        PayoutReviewRequest reviewRequest = new PayoutReviewRequest();
        reviewRequest.setNotes("Suspicious activity");

        when(payoutRequestRepository.findById(payout.getId())).thenReturn(Optional.of(payout));
        when(walletRepository.findByIdWithLock(wallet.getId())).thenReturn(Optional.of(wallet));
        when(payoutRequestRepository.save(any(PayoutRequest.class))).thenReturn(payout);
        when(walletRepository.save(any(DriverWallet.class))).thenReturn(wallet);
        when(bankAccountRepository.findById(bankAccount.getId())).thenReturn(Optional.of(bankAccount));

        // Simulate reserved funds
        wallet.setPendingPayout(payout.getAmount());
        wallet.setAvailableBalance(BigDecimal.valueOf(30000.00));

        // Act
        PayoutResponse response = payoutService.rejectPayoutRequest(payout.getId(), adminId, reviewRequest);

        // Assert
        assertThat(response.getStatus()).isEqualTo(PayoutStatus.REJECTED);
        
        // Verify funds were released
        ArgumentCaptor<DriverWallet> walletCaptor = ArgumentCaptor.forClass(DriverWallet.class);
        verify(walletRepository).save(walletCaptor.capture());
        
        DriverWallet savedWallet = walletCaptor.getValue();
        assertThat(savedWallet.getAvailableBalance()).isEqualByComparingTo(BigDecimal.valueOf(50000.00));
        assertThat(savedWallet.getPendingPayout()).isEqualByComparingTo(BigDecimal.ZERO);

        verify(payoutEventProducer).publishPayoutRejected(any(PayoutRequest.class));
    }

    @Test
    void markAsCompleted_ShouldUpdateWalletAndPayoutStatus() {
        // Arrange
        PayoutRequest payout = createProcessingPayout();
        String providerReference = "PAY_123456";

        when(payoutRequestRepository.findById(payout.getId())).thenReturn(Optional.of(payout));
        when(walletRepository.findByIdWithLock(wallet.getId())).thenReturn(Optional.of(wallet));
        when(payoutRequestRepository.save(any(PayoutRequest.class))).thenReturn(payout);
        when(walletRepository.save(any(DriverWallet.class))).thenReturn(wallet);

        // Simulate reserved funds
        wallet.setPendingPayout(payout.getAmount());

        // Act
        payoutService.markAsCompleted(payout.getId(), providerReference);

        // Assert
        ArgumentCaptor<PayoutRequest> payoutCaptor = ArgumentCaptor.forClass(PayoutRequest.class);
        verify(payoutRequestRepository).save(payoutCaptor.capture());
        
        PayoutRequest savedPayout = payoutCaptor.getValue();
        assertThat(savedPayout.getStatus()).isEqualTo(PayoutStatus.COMPLETED);
        assertThat(savedPayout.getProviderReference()).isEqualTo(providerReference);
        assertThat(savedPayout.getCompletedAt()).isNotNull();

        // Verify wallet was updated
        ArgumentCaptor<DriverWallet> walletCaptor = ArgumentCaptor.forClass(DriverWallet.class);
        verify(walletRepository).save(walletCaptor.capture());
        
        DriverWallet savedWallet = walletCaptor.getValue();
        assertThat(savedWallet.getPendingPayout()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(savedWallet.getTotalPaidOut()).isEqualByComparingTo(BigDecimal.valueOf(70000.00));

        verify(payoutEventProducer).publishPayoutCompleted(any(PayoutRequest.class));
    }

    @Test
    void markAsFailed_ShouldReleaseReservedFundsAndSetFailureReason() {
        // Arrange
        PayoutRequest payout = createProcessingPayout();
        String failureReason = "Bank transfer failed";

        when(payoutRequestRepository.findById(payout.getId())).thenReturn(Optional.of(payout));
        when(walletRepository.findByIdWithLock(wallet.getId())).thenReturn(Optional.of(wallet));
        when(payoutRequestRepository.save(any(PayoutRequest.class))).thenReturn(payout);
        when(walletRepository.save(any(DriverWallet.class))).thenReturn(wallet);

        // Simulate reserved funds
        wallet.setPendingPayout(payout.getAmount());
        wallet.setAvailableBalance(BigDecimal.valueOf(30000.00));

        // Act
        payoutService.markAsFailed(payout.getId(), failureReason);

        // Assert
        ArgumentCaptor<PayoutRequest> payoutCaptor = ArgumentCaptor.forClass(PayoutRequest.class);
        verify(payoutRequestRepository).save(payoutCaptor.capture());
        
        PayoutRequest savedPayout = payoutCaptor.getValue();
        assertThat(savedPayout.getStatus()).isEqualTo(PayoutStatus.FAILED);
        assertThat(savedPayout.getFailureReason()).isEqualTo(failureReason);

        // Verify funds were released back to available balance
        ArgumentCaptor<DriverWallet> walletCaptor = ArgumentCaptor.forClass(DriverWallet.class);
        verify(walletRepository).save(walletCaptor.capture());
        
        DriverWallet savedWallet = walletCaptor.getValue();
        assertThat(savedWallet.getAvailableBalance()).isEqualByComparingTo(BigDecimal.valueOf(50000.00));
        assertThat(savedWallet.getPendingPayout()).isEqualByComparingTo(BigDecimal.ZERO);

        verify(payoutEventProducer).publishPayoutFailed(any(PayoutRequest.class), eq(failureReason));
    }

    @Test
    void getPayoutRequests_WithStatus_ShouldReturnFilteredResults() {
        // Arrange
        PayoutRequest payout1 = createPendingPayout();
        PayoutRequest payout2 = createPendingPayout();
        
        Pageable pageable = PageRequest.of(0, 20);
        Page<PayoutRequest> payoutPage = new PageImpl<>(Arrays.asList(payout1, payout2), pageable, 2);

        when(payoutRequestRepository.findByDriverIdAndStatus(driverId, PayoutStatus.PENDING, pageable))
                .thenReturn(payoutPage);
        when(bankAccountRepository.findById(any())).thenReturn(Optional.of(bankAccount));

        // Act
        Page<PayoutResponse> result = payoutService.getPayoutRequests(driverId, PayoutStatus.PENDING, pageable);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent()).hasSize(2);
        
        verify(payoutRequestRepository).findByDriverIdAndStatus(driverId, PayoutStatus.PENDING, pageable);
    }

    private PayoutRequest createPendingPayout() {
        PayoutRequest payout = new PayoutRequest();
        payout.setId(UUID.randomUUID());
        payout.setDriverId(driverId);
        payout.setWalletId(wallet.getId());
        payout.setBankAccountId(bankAccount.getId());
        payout.setAmount(BigDecimal.valueOf(20000.00));
        payout.setStatus(PayoutStatus.PENDING);
        payout.setRequestedAt(LocalDateTime.now());
        return payout;
    }

    private PayoutRequest createProcessingPayout() {
        PayoutRequest payout = createPendingPayout();
        payout.setStatus(PayoutStatus.PROCESSING);
        payout.setReviewedBy(adminId);
        payout.setReviewedAt(LocalDateTime.now());
        payout.setProcessedAt(LocalDateTime.now());
        return payout;
    }
}
