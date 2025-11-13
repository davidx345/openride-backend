package com.openride.payments.repository;

import com.openride.payments.model.Payment;
import com.openride.payments.model.PaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Repository tests for PaymentRepository.
 */
@DataJpaTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@DisplayName("PaymentRepository Tests")
class PaymentRepositoryTest {

    @Autowired
    private PaymentRepository paymentRepository;

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
    }

    @Test
    @DisplayName("Should save and find payment by ID")
    void shouldSaveAndFindPaymentById() {
        Payment payment = createPayment(PaymentStatus.INITIATED);
        Payment saved = paymentRepository.save(payment);

        assertNotNull(saved.getId());
        
        Optional<Payment> found = paymentRepository.findById(saved.getId());
        assertTrue(found.isPresent());
        assertEquals(saved.getAmount(), found.get().getAmount());
    }

    @Test
    @DisplayName("Should find payment by Korapay reference")
    void shouldFindPaymentByKorapayReference() {
        String reference = "UNIQUE_REF_" + System.currentTimeMillis();
        Payment payment = createPayment(PaymentStatus.PENDING);
        payment.setKorapayReference(reference);
        
        paymentRepository.save(payment);

        Optional<Payment> found = paymentRepository.findByKorapayReference(reference);
        assertTrue(found.isPresent());
        assertEquals(reference, found.get().getKorapayReference());
    }

    @Test
    @DisplayName("Should find payment by idempotency key")
    void shouldFindPaymentByIdempotencyKey() {
        String idempotencyKey = "unique-key-" + UUID.randomUUID();
        Payment payment = createPayment(PaymentStatus.INITIATED);
        payment.setIdempotencyKey(idempotencyKey);
        
        paymentRepository.save(payment);

        Optional<Payment> found = paymentRepository.findByIdempotencyKey(idempotencyKey);
        assertTrue(found.isPresent());
        assertEquals(idempotencyKey, found.get().getIdempotencyKey());
    }

    @Test
    @DisplayName("Should find payment by booking ID")
    void shouldFindPaymentByBookingId() {
        UUID bookingId = UUID.randomUUID();
        Payment payment = createPayment(PaymentStatus.SUCCESS);
        payment.setBookingId(bookingId);
        
        paymentRepository.save(payment);

        Optional<Payment> found = paymentRepository.findByBookingId(bookingId);
        assertTrue(found.isPresent());
        assertEquals(bookingId, found.get().getBookingId());
    }

    @Test
    @DisplayName("Should find payments by rider ID")
    void shouldFindPaymentsByRiderId() {
        UUID riderId = UUID.randomUUID();
        
        Payment payment1 = createPayment(PaymentStatus.SUCCESS);
        payment1.setRiderId(riderId);
        
        Payment payment2 = createPayment(PaymentStatus.PENDING);
        payment2.setRiderId(riderId);
        
        paymentRepository.save(payment1);
        paymentRepository.save(payment2);

        List<Payment> payments = paymentRepository.findByRiderIdOrderByCreatedAtDesc(riderId);
        assertEquals(2, payments.size());
        assertTrue(payments.stream().allMatch(p -> p.getRiderId().equals(riderId)));
    }

    @Test
    @DisplayName("Should find payments by rider and status")
    void shouldFindPaymentsByRiderAndStatus() {
        UUID riderId = UUID.randomUUID();
        
        Payment successPayment = createPayment(PaymentStatus.SUCCESS);
        successPayment.setRiderId(riderId);
        
        Payment pendingPayment = createPayment(PaymentStatus.PENDING);
        pendingPayment.setRiderId(riderId);
        
        paymentRepository.save(successPayment);
        paymentRepository.save(pendingPayment);

        List<Payment> successPayments = paymentRepository
            .findByRiderIdAndStatus(riderId, PaymentStatus.SUCCESS);
        
        assertEquals(1, successPayments.size());
        assertEquals(PaymentStatus.SUCCESS, successPayments.get(0).getStatus());
    }

    @Test
    @DisplayName("Should find expired pending payments")
    void shouldFindExpiredPendingPayments() {
        Payment expiredPayment = createPayment(PaymentStatus.PENDING);
        expiredPayment.setExpiresAt(LocalDateTime.now().minusMinutes(10));
        
        Payment activePayment = createPayment(PaymentStatus.PENDING);
        activePayment.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        
        paymentRepository.save(expiredPayment);
        paymentRepository.save(activePayment);

        List<Payment> expired = paymentRepository.findExpiredPendingPayments(LocalDateTime.now());
        
        assertEquals(1, expired.size());
        assertEquals(expiredPayment.getId(), expired.get(0).getId());
    }

    @Test
    @DisplayName("Should check if booking has payment")
    void shouldCheckIfBookingHasPayment() {
        UUID bookingId = UUID.randomUUID();
        Payment payment = createPayment(PaymentStatus.INITIATED);
        payment.setBookingId(bookingId);
        
        paymentRepository.save(payment);

        assertTrue(paymentRepository.existsByBookingId(bookingId));
        assertFalse(paymentRepository.existsByBookingId(UUID.randomUUID()));
    }

    @Test
    @DisplayName("Should count payments by status")
    void shouldCountPaymentsByStatus() {
        paymentRepository.save(createPayment(PaymentStatus.SUCCESS));
        paymentRepository.save(createPayment(PaymentStatus.SUCCESS));
        paymentRepository.save(createPayment(PaymentStatus.PENDING));

        long successCount = paymentRepository.countByStatus(PaymentStatus.SUCCESS);
        long pendingCount = paymentRepository.countByStatus(PaymentStatus.PENDING);

        assertEquals(2, successCount);
        assertEquals(1, pendingCount);
    }

    private Payment createPayment(PaymentStatus status) {
        return Payment.builder()
            .bookingId(UUID.randomUUID())
            .riderId(UUID.randomUUID())
            .amount(new BigDecimal("5000.00"))
            .currency("NGN")
            .status(status)
            .korapayReference("TEST_REF_" + System.currentTimeMillis() + "_" + UUID.randomUUID())
            .idempotencyKey("test-key-" + UUID.randomUUID())
            .initiatedAt(LocalDateTime.now())
            .expiresAt(LocalDateTime.now().plusMinutes(15))
            .build();
    }
}
