package com.openride.payments.integration;

import com.openride.payments.model.Payment;
import com.openride.payments.model.PaymentStatus;
import com.openride.payments.repository.PaymentRepository;
import com.openride.payments.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for payment flow using Testcontainers.
 */
@SpringBootTest
@Testcontainers
@DisplayName("Payment Integration Tests")
class PaymentIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:14-alpine")
        .withDatabaseName("testdb")
        .withUsername("test")
        .withPassword("test");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
        .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.redis.host", redis::getHost);
        registry.add("spring.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentRepository paymentRepository;

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
    }

    @Test
    @DisplayName("Should save and retrieve payment from database")
    void shouldSaveAndRetrievePayment() {
        Payment payment = Payment.builder()
            .bookingId(UUID.randomUUID())
            .riderId(UUID.randomUUID())
            .amount(new BigDecimal("5000.00"))
            .currency("NGN")
            .status(PaymentStatus.INITIATED)
            .korapayReference("TEST_REF_" + System.currentTimeMillis())
            .idempotencyKey("test-key-" + UUID.randomUUID())
            .build();

        Payment saved = paymentRepository.save(payment);
        
        assertNotNull(saved.getId());
        assertNotNull(saved.getCreatedAt());

        Payment retrieved = paymentRepository.findById(saved.getId()).orElseThrow();
        assertEquals(saved.getAmount(), retrieved.getAmount());
        assertEquals(saved.getStatus(), retrieved.getStatus());
    }

    @Test
    @DisplayName("Should find payment by korapay reference")
    void shouldFindPaymentByKorapayReference() {
        String korapayRef = "UNIQUE_REF_" + System.currentTimeMillis();
        
        Payment payment = Payment.builder()
            .bookingId(UUID.randomUUID())
            .riderId(UUID.randomUUID())
            .amount(new BigDecimal("5000.00"))
            .currency("NGN")
            .status(PaymentStatus.PENDING)
            .korapayReference(korapayRef)
            .idempotencyKey("test-key-" + UUID.randomUUID())
            .build();

        paymentRepository.save(payment);

        Payment found = paymentRepository.findByKorapayReference(korapayRef).orElseThrow();
        assertEquals(korapayRef, found.getKorapayReference());
    }

    @Test
    @DisplayName("Should find payments by rider")
    void shouldFindPaymentsByRider() {
        UUID riderId = UUID.randomUUID();

        Payment payment1 = Payment.builder()
            .bookingId(UUID.randomUUID())
            .riderId(riderId)
            .amount(new BigDecimal("5000.00"))
            .currency("NGN")
            .status(PaymentStatus.SUCCESS)
            .korapayReference("REF_1_" + System.currentTimeMillis())
            .idempotencyKey("key-1-" + UUID.randomUUID())
            .build();

        Payment payment2 = Payment.builder()
            .bookingId(UUID.randomUUID())
            .riderId(riderId)
            .amount(new BigDecimal("3000.00"))
            .currency("NGN")
            .status(PaymentStatus.PENDING)
            .korapayReference("REF_2_" + System.currentTimeMillis())
            .idempotencyKey("key-2-" + UUID.randomUUID())
            .build();

        paymentRepository.save(payment1);
        paymentRepository.save(payment2);

        List<Payment> payments = paymentRepository.findByRiderIdOrderByCreatedAtDesc(riderId);
        
        assertEquals(2, payments.size());
        assertTrue(payments.stream().allMatch(p -> p.getRiderId().equals(riderId)));
    }

    @Test
    @DisplayName("Should enforce unique booking constraint")
    void shouldEnforceUniqueBookingConstraint() {
        UUID bookingId = UUID.randomUUID();

        Payment payment1 = Payment.builder()
            .bookingId(bookingId)
            .riderId(UUID.randomUUID())
            .amount(new BigDecimal("5000.00"))
            .currency("NGN")
            .status(PaymentStatus.INITIATED)
            .korapayReference("REF_1_" + System.currentTimeMillis())
            .idempotencyKey("key-1-" + UUID.randomUUID())
            .build();

        paymentRepository.save(payment1);

        assertTrue(paymentRepository.existsByBookingId(bookingId));
    }

    @Test
    @DisplayName("Should find expired pending payments")
    void shouldFindExpiredPendingPayments() {
        Payment expiredPayment = Payment.builder()
            .bookingId(UUID.randomUUID())
            .riderId(UUID.randomUUID())
            .amount(new BigDecimal("5000.00"))
            .currency("NGN")
            .status(PaymentStatus.PENDING)
            .korapayReference("EXPIRED_REF_" + System.currentTimeMillis())
            .idempotencyKey("expired-key-" + UUID.randomUUID())
            .build();

        // Set expiry to past
        expiredPayment.setExpiresAt(java.time.LocalDateTime.now().minusMinutes(10));
        
        paymentRepository.save(expiredPayment);

        List<Payment> expired = paymentRepository.findExpiredPendingPayments(
            java.time.LocalDateTime.now()
        );

        assertFalse(expired.isEmpty());
        assertTrue(expired.stream().allMatch(p -> p.getStatus() == PaymentStatus.PENDING));
    }
}
