package com.openride.payments.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Entity representing an event in the payment lifecycle.
 * Provides audit trail for all payment status changes.
 */
@Entity
@Table(name = "payment_events", indexes = {
    @Index(name = "idx_payment_events_payment", columnList = "payment_id,created_at"),
    @Index(name = "idx_payment_events_type", columnList = "event_type")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", length = 20)
    private PaymentStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 20)
    private PaymentStatus newStatus;

    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Creates a payment event for a status transition.
     */
    public static PaymentEvent forStatusChange(Payment payment, PaymentStatus oldStatus, String eventType) {
        return PaymentEvent.builder()
            .paymentId(payment.getId())
            .eventType(eventType)
            .previousStatus(oldStatus)
            .newStatus(payment.getStatus())
            .metadata(Map.of(
                "booking_id", payment.getBookingId().toString(),
                "amount", payment.getAmount().toString(),
                "currency", payment.getCurrency()
            ))
            .build();
    }
}
