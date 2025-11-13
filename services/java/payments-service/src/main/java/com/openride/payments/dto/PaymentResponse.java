package com.openride.payments.dto;

import com.openride.payments.model.Payment;
import com.openride.payments.model.PaymentMethod;
import com.openride.payments.model.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for payment operations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {

    private UUID id;
    private UUID bookingId;
    private UUID riderId;
    private BigDecimal amount;
    private String currency;
    private PaymentStatus status;
    private PaymentMethod paymentMethod;
    private String korapayReference;
    private String korapayTransactionId;
    private String korapayCheckoutUrl;
    private String failureReason;
    private BigDecimal refundAmount;
    private LocalDateTime refundedAt;
    private LocalDateTime initiatedAt;
    private LocalDateTime completedAt;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;

    /**
     * Creates response from payment entity.
     */
    public static PaymentResponse fromEntity(Payment payment) {
        return PaymentResponse.builder()
            .id(payment.getId())
            .bookingId(payment.getBookingId())
            .riderId(payment.getRiderId())
            .amount(payment.getAmount())
            .currency(payment.getCurrency())
            .status(payment.getStatus())
            .paymentMethod(payment.getPaymentMethod())
            .korapayReference(payment.getKorapayReference())
            .korapayTransactionId(payment.getKorapayTransactionId())
            .korapayCheckoutUrl(payment.getKorapayCheckoutUrl())
            .failureReason(payment.getFailureReason())
            .refundAmount(payment.getRefundAmount())
            .refundedAt(payment.getRefundedAt())
            .initiatedAt(payment.getInitiatedAt())
            .completedAt(payment.getCompletedAt())
            .expiresAt(payment.getExpiresAt())
            .createdAt(payment.getCreatedAt())
            .build();
    }
}
