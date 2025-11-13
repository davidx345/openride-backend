package com.openride.payments.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity representing a payment transaction.
 * Tracks payment lifecycle from initiation through Korapay to completion.
 */
@Entity
@Table(name = "payments", indexes = {
    @Index(name = "idx_payments_booking", columnList = "booking_id"),
    @Index(name = "idx_payments_rider", columnList = "rider_id"),
    @Index(name = "idx_payments_status", columnList = "status"),
    @Index(name = "idx_payments_korapay_ref", columnList = "korapay_reference"),
    @Index(name = "idx_payments_idempotency", columnList = "idempotency_key")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "booking_id", nullable = false)
    private UUID bookingId;

    @Column(name = "rider_id", nullable = false)
    private UUID riderId;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency = "NGN";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status = PaymentStatus.INITIATED;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 20)
    private PaymentMethod paymentMethod;

    // Korapay specific fields
    @Column(name = "korapay_reference", nullable = false, unique = true)
    private String korapayReference;

    @Column(name = "korapay_transaction_id", unique = true)
    private String korapayTransactionId;

    @Column(name = "korapay_checkout_url", columnDefinition = "TEXT")
    private String korapayCheckoutUrl;

    @Column(name = "korapay_customer_email")
    private String korapayCustomerEmail;

    @Column(name = "korapay_customer_name")
    private String korapayCustomerName;

    // Metadata
    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "refund_reason", columnDefinition = "TEXT")
    private String refundReason;

    @Column(name = "refund_amount", precision = 10, scale = 2)
    private BigDecimal refundAmount;

    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;

    // Timestamps
    @Column(name = "initiated_at", nullable = false)
    private LocalDateTime initiatedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Checks if the payment has expired.
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt) && status == PaymentStatus.PENDING;
    }

    /**
     * Checks if the payment can be refunded.
     */
    public boolean canBeRefunded() {
        return status.isRefundable() && refundedAt == null;
    }

    /**
     * Marks the payment as completed with the given transaction ID.
     */
    public void markAsCompleted(String transactionId, PaymentMethod method) {
        this.status = PaymentStatus.SUCCESS;
        this.korapayTransactionId = transactionId;
        this.paymentMethod = method;
        this.completedAt = LocalDateTime.now();
    }

    /**
     * Marks the payment as failed with the given reason.
     */
    public void markAsFailed(String reason) {
        this.status = PaymentStatus.FAILED;
        this.failureReason = reason;
        this.completedAt = LocalDateTime.now();
    }

    /**
     * Marks the payment as refunded.
     */
    public void markAsRefunded(BigDecimal amount, String reason) {
        this.status = PaymentStatus.REFUNDED;
        this.refundAmount = amount;
        this.refundReason = reason;
        this.refundedAt = LocalDateTime.now();
    }
}
