package com.openride.payouts.service;

import com.openride.payouts.dto.SettlementResponse;
import com.openride.payouts.exception.PayoutsException;
import com.openride.payouts.integration.PaymentProvider;
import com.openride.payouts.integration.PaymentProviderFactory;
import com.openride.payouts.model.BankAccount;
import com.openride.payouts.model.PayoutRequest;
import com.openride.payouts.model.Settlement;
import com.openride.payouts.model.enums.PayoutStatus;
import com.openride.payouts.model.enums.SettlementStatus;
import com.openride.payouts.repository.BankAccountRepository;
import com.openride.payouts.repository.PayoutRequestRepository;
import com.openride.payouts.repository.SettlementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Service for managing settlement batches.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementService {

    private final SettlementRepository settlementRepository;
    private final PayoutRequestRepository payoutRequestRepository;
    private final BankAccountRepository bankAccountRepository;
    private final PaymentProviderFactory paymentProviderFactory;
    private final PayoutService payoutService;
    private final AuditService auditService;

    /**
     * Create a new settlement batch from approved payouts.
     */
    @Transactional
    public SettlementResponse createSettlementBatch(UUID initiatedBy) {
        log.info("Creating settlement batch initiated by: {}", initiatedBy);

        // Get all approved payouts ready for settlement
        List<PayoutRequest> approvedPayouts = payoutRequestRepository.findApprovedPayoutsForSettlement();

        if (approvedPayouts.isEmpty()) {
            throw new PayoutsException("No approved payouts found for settlement");
        }

        // Calculate total amount
        BigDecimal totalAmount = approvedPayouts.stream()
                .map(PayoutRequest::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Create settlement
        Settlement settlement = new Settlement();
        settlement.setBatchReference(settlement.generateBatchReference());
        settlement.setTotalAmount(totalAmount);
        settlement.setPayoutCount(approvedPayouts.size());
        settlement.setStatus(SettlementStatus.PENDING);
        settlement.setInitiatedBy(initiatedBy);
        settlement.setInitiatedAt(LocalDateTime.now());

        Settlement saved = settlementRepository.save(settlement);

        // Associate payouts with settlement
        for (PayoutRequest payout : approvedPayouts) {
            payoutService.markAsProcessing(payout.getId(), saved.getId());
        }

        auditService.logAuditEntry("SETTLEMENT", saved.getId(), "CREATE_BATCH", initiatedBy, null, null);

        log.info("Settlement batch created: {}, payouts: {}, total: {}", 
                saved.getId(), approvedPayouts.size(), totalAmount);

        return mapToResponse(saved);
    }

    /**
     * Process a settlement batch.
     */
    @Transactional
    public SettlementResponse processSettlement(UUID settlementId) {
        log.info("Processing settlement: {}", settlementId);

        Settlement settlement = settlementRepository.findById(settlementId)
                .orElseThrow(() -> new PayoutsException("Settlement not found"));

        if (settlement.getStatus() != SettlementStatus.PENDING) {
            throw new PayoutsException("Settlement is not pending");
        }

        settlement.markAsProcessing();
        settlementRepository.save(settlement);

        // Get all payouts in this settlement
        List<PayoutRequest> payouts = payoutRequestRepository.findBySettlementId(settlementId);

        // Select payment provider (could be based on config or other criteria)
        PaymentProvider provider = paymentProviderFactory.getProvider();
        settlement.setProvider(provider.getProviderName());

        int successCount = 0;
        int failureCount = 0;

        // Process each payout
        for (PayoutRequest payout : payouts) {
            try {
                BankAccount bankAccount = bankAccountRepository.findById(payout.getBankAccountId())
                        .orElseThrow(() -> new PayoutsException("Bank account not found"));

                // Initiate bank transfer
                String providerReference = provider.initiateBankTransfer(
                        bankAccount.getAccountNumber(),
                        bankAccount.getBankCode(),
                        payout.getAmount(),
                        "Payout for trip earnings"
                );

                // Mark payout as completed
                payoutService.markAsCompleted(payout.getId(), providerReference);
                successCount++;

                log.info("Payout processed successfully: {}, provider reference: {}", 
                        payout.getId(), providerReference);

            } catch (Exception e) {
                log.error("Failed to process payout: {}", payout.getId(), e);
                payoutService.markAsFailed(payout.getId(), e.getMessage());
                failureCount++;
            }
        }

        // Update settlement status
        if (failureCount == 0) {
            settlement.markAsCompleted();
            log.info("Settlement completed successfully: {}, payouts: {}", settlementId, successCount);
        } else {
            String failureReason = String.format("Partial success: %d succeeded, %d failed", 
                    successCount, failureCount);
            settlement.markAsFailed(failureReason);
            log.error("Settlement partially failed: {}, reason: {}", settlementId, failureReason);
        }

        Settlement saved = settlementRepository.save(settlement);

        auditService.logAuditEntry("SETTLEMENT", settlementId, "PROCESS_SETTLEMENT", null, null, null);

        return mapToResponse(saved);
    }

    /**
     * Get settlements.
     */
    @Transactional(readOnly = true)
    public Page<SettlementResponse> getSettlements(SettlementStatus status, Pageable pageable) {
        log.debug("Getting settlements with status: {}", status);

        Page<Settlement> settlements;
        if (status != null) {
            settlements = settlementRepository.findByStatus(status, pageable);
        } else {
            settlements = settlementRepository.findAll(pageable);
        }

        return settlements.map(this::mapToResponse);
    }

    /**
     * Get settlement by ID.
     */
    @Transactional(readOnly = true)
    public SettlementResponse getSettlement(UUID settlementId) {
        log.debug("Getting settlement: {}", settlementId);
        Settlement settlement = settlementRepository.findById(settlementId)
                .orElseThrow(() -> new PayoutsException("Settlement not found"));
        return mapToResponse(settlement);
    }

    /**
     * Retry failed settlement.
     */
    @Transactional
    public SettlementResponse retrySettlement(UUID settlementId) {
        log.info("Retrying failed settlement: {}", settlementId);

        Settlement settlement = settlementRepository.findById(settlementId)
                .orElseThrow(() -> new PayoutsException("Settlement not found"));

        if (settlement.getStatus() != SettlementStatus.FAILED) {
            throw new PayoutsException("Only failed settlements can be retried");
        }

        // Reset settlement status
        settlement.setStatus(SettlementStatus.PENDING);
        settlement.setFailureReason(null);
        settlementRepository.save(settlement);

        // Process settlement
        return processSettlement(settlementId);
    }

    private SettlementResponse mapToResponse(Settlement settlement) {
        return SettlementResponse.builder()
                .id(settlement.getId())
                .batchReference(settlement.getBatchReference())
                .totalAmount(settlement.getTotalAmount())
                .payoutCount(settlement.getPayoutCount())
                .status(settlement.getStatus())
                .initiatedBy(settlement.getInitiatedBy())
                .initiatedAt(settlement.getInitiatedAt())
                .completedAt(settlement.getCompletedAt())
                .failureReason(settlement.getFailureReason())
                .provider(settlement.getProvider())
                .providerReference(settlement.getProviderReference())
                .createdAt(settlement.getCreatedAt())
                .build();
    }
}
