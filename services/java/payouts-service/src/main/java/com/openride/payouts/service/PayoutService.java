package com.openride.payouts.service;

import com.openride.payouts.config.FinancialConfig;
import com.openride.payouts.dto.PayoutRequestDto;
import com.openride.payouts.dto.PayoutResponse;
import com.openride.payouts.dto.PayoutReviewRequest;
import com.openride.payouts.exception.*;
import com.openride.payouts.model.BankAccount;
import com.openride.payouts.model.DriverWallet;
import com.openride.payouts.model.PayoutRequest;
import com.openride.payouts.model.enums.PayoutStatus;
import com.openride.payouts.repository.BankAccountRepository;
import com.openride.payouts.repository.DriverWalletRepository;
import com.openride.payouts.repository.PayoutRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Service for managing payout requests.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayoutService {

    private final PayoutRequestRepository payoutRequestRepository;
    private final DriverWalletRepository walletRepository;
    private final BankAccountRepository bankAccountRepository;
    private final FinancialConfig financialConfig;
    private final PayoutEventProducer payoutEventProducer;
    private final AuditService auditService;

    /**
     * Request a payout.
     */
    @Transactional
    public PayoutResponse requestPayout(UUID driverId, PayoutRequestDto request) {
        log.info("Processing payout request for driver: {}, amount: {}", driverId, request.getAmount());

        // Validate payout request
        validatePayoutRequest(driverId, request.getAmount(), request.getBankAccountId());

        // Lock wallet to prevent concurrent modifications
        DriverWallet wallet = walletRepository.findByDriverIdWithLock(driverId)
                .orElseThrow(() -> WalletNotFoundException.forDriver(driverId));

        // Get bank account
        BankAccount bankAccount = bankAccountRepository.findById(request.getBankAccountId())
                .orElseThrow(() -> new PayoutsException("Bank account not found"));

        if (!bankAccount.getDriverId().equals(driverId)) {
            throw new PayoutsException("Bank account does not belong to driver");
        }

        // Reserve amount in wallet
        wallet.reserveForPayout(request.getAmount());
        walletRepository.save(wallet);

        // Create payout request
        PayoutRequest payoutRequest = new PayoutRequest();
        payoutRequest.setDriverId(driverId);
        payoutRequest.setWalletId(wallet.getId());
        payoutRequest.setAmount(request.getAmount());
        payoutRequest.setBankAccountId(bankAccount.getId());
        payoutRequest.setStatus(PayoutStatus.PENDING);
        payoutRequest.setRequestedAt(LocalDateTime.now());

        PayoutRequest saved = payoutRequestRepository.save(payoutRequest);

        // Publish event
        payoutEventProducer.publishPayoutRequested(saved);

        auditService.logAuditEntry("PAYOUT_REQUEST", saved.getId(), "REQUEST_PAYOUT", driverId, null, null);

        log.info("Payout request created: {}", saved.getId());
        return mapToResponse(saved, bankAccount);
    }

    /**
     * Get payout requests for driver.
     */
    @Transactional(readOnly = true)
    public Page<PayoutResponse> getPayoutRequests(UUID driverId, PayoutStatus status, Pageable pageable) {
        log.debug("Getting payout requests for driver: {}, status: {}", driverId, status);

        Page<PayoutRequest> requests;
        if (status != null) {
            requests = payoutRequestRepository.findByDriverIdAndStatus(driverId, status, pageable);
        } else {
            requests = payoutRequestRepository.findByDriverId(driverId, pageable);
        }

        return requests.map(request -> {
            BankAccount bankAccount = bankAccountRepository.findById(request.getBankAccountId())
                    .orElse(null);
            return mapToResponse(request, bankAccount);
        });
    }

    /**
     * Get pending payout requests for admin review.
     */
    @Transactional(readOnly = true)
    public Page<PayoutResponse> getPendingPayouts(Pageable pageable) {
        log.debug("Getting pending payout requests");
        return payoutRequestRepository.findByStatus(PayoutStatus.PENDING, pageable)
                .map(request -> {
                    BankAccount bankAccount = bankAccountRepository.findById(request.getBankAccountId())
                            .orElse(null);
                    return mapToResponse(request, bankAccount);
                });
    }

    /**
     * Approve payout request (admin only).
     */
    @Transactional
    public PayoutResponse approvePayoutRequest(UUID payoutId, UUID adminId, PayoutReviewRequest reviewRequest) {
        log.info("Approving payout request: {} by admin: {}", payoutId, adminId);

        PayoutRequest payout = payoutRequestRepository.findById(payoutId)
                .orElseThrow(() -> new PayoutsException("Payout request not found"));

        if (payout.getStatus() != PayoutStatus.PENDING) {
            throw new PayoutsException("Payout request is not pending");
        }

        payout.approve(adminId, reviewRequest.getNotes());
        PayoutRequest saved = payoutRequestRepository.save(payout);

        // Publish event
        payoutEventProducer.publishPayoutApproved(saved);

        auditService.logAuditEntry("PAYOUT_REQUEST", payoutId, "APPROVE_PAYOUT", adminId, null, null);

        log.info("Payout request approved: {}", payoutId);
        BankAccount bankAccount = bankAccountRepository.findById(saved.getBankAccountId()).orElse(null);
        return mapToResponse(saved, bankAccount);
    }

    /**
     * Reject payout request (admin only).
     */
    @Transactional
    public PayoutResponse rejectPayoutRequest(UUID payoutId, UUID adminId, PayoutReviewRequest reviewRequest) {
        log.info("Rejecting payout request: {} by admin: {}", payoutId, adminId);

        PayoutRequest payout = payoutRequestRepository.findById(payoutId)
                .orElseThrow(() -> new PayoutsException("Payout request not found"));

        if (payout.getStatus() != PayoutStatus.PENDING) {
            throw new PayoutsException("Payout request is not pending");
        }

        // Release reserved amount in wallet
        DriverWallet wallet = walletRepository.findByIdWithLock(payout.getWalletId())
                .orElseThrow(() -> WalletNotFoundException.forWallet(payout.getWalletId()));

        wallet.releaseReservedAmount(payout.getAmount());
        walletRepository.save(wallet);

        payout.reject(adminId, reviewRequest.getNotes());
        PayoutRequest saved = payoutRequestRepository.save(payout);

        // Publish event
        payoutEventProducer.publishPayoutRejected(saved);

        auditService.logAuditEntry("PAYOUT_REQUEST", payoutId, "REJECT_PAYOUT", adminId, null, null);

        log.info("Payout request rejected: {}", payoutId);
        BankAccount bankAccount = bankAccountRepository.findById(saved.getBankAccountId()).orElse(null);
        return mapToResponse(saved, bankAccount);
    }

    /**
     * Mark payout as processing.
     */
    @Transactional
    public void markAsProcessing(UUID payoutId, UUID settlementId) {
        log.info("Marking payout as processing: {}, settlement: {}", payoutId, settlementId);

        PayoutRequest payout = payoutRequestRepository.findById(payoutId)
                .orElseThrow(() -> new PayoutsException("Payout request not found"));

        payout.markAsProcessing(settlementId);
        payoutRequestRepository.save(payout);

        auditService.logAuditEntry("PAYOUT_REQUEST", payoutId, "MARK_PROCESSING", null, null, null);
    }

    /**
     * Mark payout as completed.
     */
    @Transactional
    public void markAsCompleted(UUID payoutId, String providerReference) {
        log.info("Marking payout as completed: {}, provider reference: {}", payoutId, providerReference);

        PayoutRequest payout = payoutRequestRepository.findById(payoutId)
                .orElseThrow(() -> new PayoutsException("Payout request not found"));

        // Complete payout in wallet
        DriverWallet wallet = walletRepository.findByIdWithLock(payout.getWalletId())
                .orElseThrow(() -> WalletNotFoundException.forWallet(payout.getWalletId()));

        wallet.completePayout(payout.getAmount());
        walletRepository.save(wallet);

        payout.markAsCompleted(providerReference);
        PayoutRequest saved = payoutRequestRepository.save(payout);

        // Publish event
        payoutEventProducer.publishPayoutCompleted(saved);

        auditService.logAuditEntry("PAYOUT_REQUEST", payoutId, "COMPLETE_PAYOUT", null, null, null);

        log.info("Payout completed: {}", payoutId);
    }

    /**
     * Mark payout as failed.
     */
    @Transactional
    public void markAsFailed(UUID payoutId, String failureReason) {
        log.error("Marking payout as failed: {}, reason: {}", payoutId, failureReason);

        PayoutRequest payout = payoutRequestRepository.findById(payoutId)
                .orElseThrow(() -> new PayoutsException("Payout request not found"));

        // Release reserved amount in wallet
        DriverWallet wallet = walletRepository.findByIdWithLock(payout.getWalletId())
                .orElseThrow(() -> WalletNotFoundException.forWallet(payout.getWalletId()));

        wallet.releaseReservedAmount(payout.getAmount());
        walletRepository.save(wallet);

        payout.markAsFailed(failureReason);
        PayoutRequest saved = payoutRequestRepository.save(payout);

        // Publish event
        payoutEventProducer.publishPayoutFailed(saved, failureReason);

        auditService.logAuditEntry("PAYOUT_REQUEST", payoutId, "FAIL_PAYOUT", null, null, null);

        log.error("Payout failed: {}", payoutId);
    }

    private void validatePayoutRequest(UUID driverId, BigDecimal amount, UUID bankAccountId) {
        // Check minimum payout amount
        BigDecimal minimumPayout = financialConfig.getPayout().getMinimumAmount();
        if (amount.compareTo(minimumPayout) < 0) {
            throw new MinimumPayoutAmountException(minimumPayout);
        }

        // Check if wallet exists and has sufficient balance
        DriverWallet wallet = walletRepository.findByDriverId(driverId)
                .orElseThrow(() -> WalletNotFoundException.forDriver(driverId));

        if (wallet.getAvailableBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException(amount, wallet.getAvailableBalance());
        }

        // Check if bank account is verified
        BankAccount bankAccount = bankAccountRepository.findById(bankAccountId)
                .orElseThrow(() -> new PayoutsException("Bank account not found"));

        if (!bankAccount.getIsVerified()) {
            throw new BankAccountNotVerifiedException(bankAccountId);
        }

        // Check for existing pending payout
        if (payoutRequestRepository.hasPendingPayout(driverId)) {
            throw new PendingPayoutExistsException(driverId);
        }
    }

    private PayoutResponse mapToResponse(PayoutRequest payout, BankAccount bankAccount) {
        PayoutResponse.PayoutResponseBuilder builder = PayoutResponse.builder()
                .id(payout.getId())
                .driverId(payout.getDriverId())
                .walletId(payout.getWalletId())
                .amount(payout.getAmount())
                .status(payout.getStatus())
                .requestedAt(payout.getRequestedAt())
                .reviewedAt(payout.getReviewedAt())
                .reviewedBy(payout.getReviewedBy())
                .reviewNotes(payout.getReviewNotes())
                .settlementId(payout.getSettlementId())
                .processedAt(payout.getProcessedAt())
                .completedAt(payout.getCompletedAt())
                .failureReason(payout.getFailureReason())
                .providerReference(payout.getProviderReference())
                .createdAt(payout.getCreatedAt());

        if (bankAccount != null) {
            builder.bankAccountNumber(bankAccount.getMaskedAccountNumber())
                    .bankName(bankAccount.getBankName());
        }

        return builder.build();
    }
}
