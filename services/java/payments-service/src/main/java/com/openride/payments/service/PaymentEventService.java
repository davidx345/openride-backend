package com.openride.payments.service;

import com.openride.payments.model.Payment;
import com.openride.payments.model.PaymentEvent;
import com.openride.payments.model.PaymentStatus;
import com.openride.payments.repository.PaymentEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service for managing payment events and audit trail.
 * Records all significant payment state changes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentEventService {

    private final PaymentEventRepository paymentEventRepository;

    /**
     * Logs a payment event.
     *
     * @param payment payment entity
     * @param previousStatus previous status before transition
     * @param eventType event type (e.g., "PAYMENT_INITIATED", "PAYMENT_CONFIRMED")
     */
    @Transactional
    public void logEvent(Payment payment, PaymentStatus previousStatus, String eventType) {
        PaymentEvent event = PaymentEvent.forStatusChange(payment, previousStatus, eventType);
        paymentEventRepository.save(event);
        log.debug("Payment event logged: paymentId={}, event={}", payment.getId(), eventType);
    }

    /**
     * Logs a payment event with custom metadata.
     *
     * @param paymentId payment ID
     * @param eventType event type
     * @param previousStatus previous status
     * @param newStatus new status
     * @param metadata additional metadata
     */
    @Transactional
    public void logEvent(UUID paymentId, String eventType, PaymentStatus previousStatus, 
                        PaymentStatus newStatus, Map<String, Object> metadata) {
        PaymentEvent event = PaymentEvent.builder()
            .paymentId(paymentId)
            .eventType(eventType)
            .previousStatus(previousStatus)
            .newStatus(newStatus)
            .metadata(metadata)
            .build();
        
        paymentEventRepository.save(event);
        log.debug("Payment event logged: paymentId={}, event={}", paymentId, eventType);
    }

    /**
     * Retrieves all events for a payment.
     *
     * @param paymentId payment ID
     * @return list of events ordered by creation time (newest first)
     */
    @Transactional(readOnly = true)
    public List<PaymentEvent> getPaymentHistory(UUID paymentId) {
        return paymentEventRepository.findByPaymentIdOrderByCreatedAtDesc(paymentId);
    }
}
