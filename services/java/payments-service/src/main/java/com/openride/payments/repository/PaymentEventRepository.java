package com.openride.payments.repository;

import com.openride.payments.model.PaymentEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Repository for PaymentEvent entity operations.
 * Provides audit trail access for payment events.
 */
@Repository
public interface PaymentEventRepository extends JpaRepository<PaymentEvent, UUID> {

    /**
     * Finds all events for a specific payment, ordered by creation time descending.
     */
    @Query("SELECT pe FROM PaymentEvent pe WHERE pe.paymentId = :paymentId ORDER BY pe.createdAt DESC")
    List<PaymentEvent> findByPaymentIdOrderByCreatedAtDesc(@Param("paymentId") UUID paymentId);

    /**
     * Finds events by type within a date range.
     */
    @Query("SELECT pe FROM PaymentEvent pe WHERE pe.eventType = :eventType AND pe.createdAt >= :startDate AND pe.createdAt < :endDate ORDER BY pe.createdAt")
    List<PaymentEvent> findByEventTypeAndCreatedAtBetween(
        @Param("eventType") String eventType,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );
}
