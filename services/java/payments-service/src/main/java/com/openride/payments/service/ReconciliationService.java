package com.openride.payments.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openride.payments.korapay.KorapayClient;
import com.openride.payments.korapay.dto.KorapayVerifyResponse;
import com.openride.payments.model.Payment;
import com.openride.payments.model.PaymentStatus;
import com.openride.payments.model.ReconciliationRecord;
import com.openride.payments.repository.PaymentRepository;
import com.openride.payments.repository.ReconciliationRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Service for payment reconciliation with Korapay.
 * Compares local payment records with Korapay records to detect discrepancies.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReconciliationService {

    private final PaymentRepository paymentRepository;
    private final ReconciliationRecordRepository reconciliationRecordRepository;
    private final KorapayClient korapayClient;
    private final ObjectMapper objectMapper;

    /**
     * Runs reconciliation for a specific date.
     * Compares local successful payments with Korapay records.
     *
     * @param date date to reconcile
     * @return reconciliation record
     */
    @Transactional
    public ReconciliationRecord reconcilePayments(LocalDate date) {
        log.info("Starting reconciliation for date: {}", date);

        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.plusDays(1).atStartOfDay();

        // Get local successful payments for the day
        List<Payment> localPayments = paymentRepository.findByStatusAndConfirmedAtBetween(
            PaymentStatus.SUCCESS,
            startOfDay,
            endOfDay
        );

        if (localPayments.isEmpty()) {
            log.info("No successful payments found for date: {}", date);
            return createReconciliationRecord(date, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO, 
                List.of(), ReconciliationRecord.ReconciliationStatus.MATCHED);
        }

        log.info("Found {} local successful payments for {}", localPayments.size(), date);

        // Verify each payment with Korapay
        List<DiscrepancyInfo> discrepancies = new ArrayList<>();
        int korapayMatchCount = 0;
        BigDecimal totalLocalAmount = BigDecimal.ZERO;
        BigDecimal totalKorapayAmount = BigDecimal.ZERO;

        for (Payment payment : localPayments) {
            totalLocalAmount = totalLocalAmount.add(payment.getAmount());

            try {
                KorapayVerifyResponse verifyResponse = korapayClient.verifyCharge(
                    payment.getKorapayReference()
                );

                if (verifyResponse.isSuccess()) {
                    BigDecimal korapayAmount = KorapayVerifyResponse.fromKobo(verifyResponse.getAmount());
                    totalKorapayAmount = totalKorapayAmount.add(korapayAmount);
                    korapayMatchCount++;

                    // Check for amount mismatch
                    if (payment.getAmount().compareTo(korapayAmount) != 0) {
                        discrepancies.add(DiscrepancyInfo.builder()
                            .paymentId(payment.getId().toString())
                            .korapayReference(payment.getKorapayReference())
                            .type("AMOUNT_MISMATCH")
                            .localAmount(payment.getAmount())
                            .korapayAmount(korapayAmount)
                            .message("Amount mismatch: local=" + payment.getAmount() + 
                                    ", korapay=" + korapayAmount)
                            .build());
                    }
                } else {
                    // Payment marked as successful locally but not in Korapay
                    discrepancies.add(DiscrepancyInfo.builder()
                        .paymentId(payment.getId().toString())
                        .korapayReference(payment.getKorapayReference())
                        .type("STATUS_MISMATCH")
                        .localAmount(payment.getAmount())
                        .message("Local payment successful but Korapay shows: " + 
                                verifyResponse.getStatus())
                        .build());
                }
            } catch (Exception e) {
                log.error("Failed to verify payment with Korapay: reference={}", 
                    payment.getKorapayReference(), e);
                discrepancies.add(DiscrepancyInfo.builder()
                    .paymentId(payment.getId().toString())
                    .korapayReference(payment.getKorapayReference())
                    .type("VERIFICATION_FAILED")
                    .localAmount(payment.getAmount())
                    .message("Korapay verification failed: " + e.getMessage())
                    .build());
            }
        }

        ReconciliationRecord.ReconciliationStatus status = discrepancies.isEmpty() 
            ? ReconciliationRecord.ReconciliationStatus.MATCHED
            : ReconciliationRecord.ReconciliationStatus.DISCREPANCY;

        ReconciliationRecord record = createReconciliationRecord(
            date,
            localPayments.size(),
            korapayMatchCount,
            totalLocalAmount,
            totalKorapayAmount,
            discrepancies,
            status
        );

        log.info("Reconciliation completed for {}: status={}, discrepancies={}", 
            date, status, discrepancies.size());

        return record;
    }

    /**
     * Gets latest reconciliation records.
     *
     * @param limit number of records to retrieve
     * @return list of reconciliation records
     */
    @Transactional(readOnly = true)
    public List<ReconciliationRecord> getLatestReconciliations(int limit) {
        List<ReconciliationRecord> records = reconciliationRecordRepository
            .findAllByOrderByReconciliationDateDesc();
        
        return records.stream()
            .limit(limit)
            .toList();
    }

    /**
     * Gets reconciliation record for a specific date.
     *
     * @param date reconciliation date
     * @return reconciliation record or null if not found
     */
    @Transactional(readOnly = true)
    public ReconciliationRecord getReconciliationForDate(LocalDate date) {
        return reconciliationRecordRepository.findByReconciliationDate(date)
            .orElse(null);
    }

    /**
     * Gets all reconciliations with discrepancies.
     *
     * @return list of records with discrepancies
     */
    @Transactional(readOnly = true)
    public List<ReconciliationRecord> getDiscrepancies() {
        return reconciliationRecordRepository.findByStatusOrderByReconciliationDateDesc(
            ReconciliationRecord.ReconciliationStatus.DISCREPANCY
        );
    }

    /**
     * Creates and saves a reconciliation record.
     */
    private ReconciliationRecord createReconciliationRecord(
            LocalDate date,
            int localCount,
            int korapayCount,
            BigDecimal localAmount,
            BigDecimal korapayAmount,
            List<DiscrepancyInfo> discrepancies,
            ReconciliationRecord.ReconciliationStatus status
    ) {
        String discrepanciesJson = null;
        if (!discrepancies.isEmpty()) {
            try {
                discrepanciesJson = objectMapper.writeValueAsString(discrepancies);
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize discrepancies", e);
            }
        }

        ReconciliationRecord record = ReconciliationRecord.builder()
            .reconciliationDate(date)
            .totalLocalPayments(localCount)
            .totalKorapayPayments(korapayCount)
            .totalLocalAmount(localAmount)
            .totalKorapayAmount(korapayAmount)
            .discrepancyCount(discrepancies.size())
            .status(status)
            .discrepancies(discrepanciesJson)
            .notes(generateNotes(localCount, korapayCount, localAmount, korapayAmount, 
                discrepancies.size()))
            .build();

        return reconciliationRecordRepository.save(record);
    }

    /**
     * Generates notes for reconciliation record.
     */
    private String generateNotes(
            int localCount,
            int korapayCount,
            BigDecimal localAmount,
            BigDecimal korapayAmount,
            int discrepancyCount
    ) {
        StringBuilder notes = new StringBuilder();
        notes.append("Local: ").append(localCount).append(" payments, ")
            .append(localAmount).append(" total. ");
        notes.append("Korapay: ").append(korapayCount).append(" payments, ")
            .append(korapayAmount).append(" total. ");
        
        if (discrepancyCount > 0) {
            notes.append("Found ").append(discrepancyCount).append(" discrepancies.");
        } else {
            notes.append("All payments matched.");
        }

        return notes.toString();
    }

    /**
     * Inner class to hold discrepancy information.
     */
    @lombok.Data
    @lombok.Builder
    private static class DiscrepancyInfo {
        private String paymentId;
        private String korapayReference;
        private String type;
        private BigDecimal localAmount;
        private BigDecimal korapayAmount;
        private String message;
    }
}
