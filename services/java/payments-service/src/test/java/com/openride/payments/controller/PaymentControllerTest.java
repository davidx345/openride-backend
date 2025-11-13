package com.openride.payments.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openride.payments.dto.InitiatePaymentRequest;
import com.openride.payments.model.Payment;
import com.openride.payments.model.PaymentStatus;
import com.openride.payments.security.UserPrincipal;
import com.openride.payments.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for PaymentController.
 */
@WebMvcTest(PaymentController.class)
@DisplayName("PaymentController Tests")
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PaymentService paymentService;

    private UUID riderId;
    private UUID bookingId;
    private Payment testPayment;

    @BeforeEach
    void setUp() {
        riderId = UUID.randomUUID();
        bookingId = UUID.randomUUID();
        
        testPayment = Payment.builder()
            .id(UUID.randomUUID())
            .bookingId(bookingId)
            .riderId(riderId)
            .amount(new BigDecimal("5000.00"))
            .currency("NGN")
            .status(PaymentStatus.PENDING)
            .korapayReference("TEST_REF_123")
            .korapayCheckoutUrl("https://checkout.korapay.com/test")
            .idempotencyKey("test-key-123")
            .initiatedAt(LocalDateTime.now())
            .expiresAt(LocalDateTime.now().plusMinutes(15))
            .build();
    }

    @Test
    @DisplayName("Should initiate payment successfully")
    @WithMockUser(roles = "RIDER")
    void shouldInitiatePaymentSuccessfully() throws Exception {
        InitiatePaymentRequest request = new InitiatePaymentRequest();
        request.setBookingId(bookingId);
        request.setAmount(new BigDecimal("5000.00"));
        request.setCurrency("NGN");
        request.setCustomerEmail("test@example.com");
        request.setCustomerName("Test User");
        request.setIdempotencyKey("test-key-123");

        when(paymentService.initiatePayment(
            eq(bookingId),
            any(UUID.class),
            eq(new BigDecimal("5000.00")),
            eq("test@example.com"),
            eq("Test User"),
            eq("test-key-123")
        )).thenReturn(testPayment);

        UserPrincipal principal = new UserPrincipal(riderId, "test@example.com", "RIDER");

        mockMvc.perform(post("/v1/payments/initiate")
                .with(user(principal))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.checkoutUrl").value("https://checkout.korapay.com/test"));
    }

    @Test
    @DisplayName("Should return 400 for invalid payment request")
    @WithMockUser(roles = "RIDER")
    void shouldReturn400ForInvalidPaymentRequest() throws Exception {
        InitiatePaymentRequest request = new InitiatePaymentRequest();
        // Missing required fields

        UserPrincipal principal = new UserPrincipal(riderId, "test@example.com", "RIDER");

        mockMvc.perform(post("/v1/payments/initiate")
                .with(user(principal))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should get payment by ID")
    @WithMockUser(roles = "RIDER")
    void shouldGetPaymentById() throws Exception {
        when(paymentService.getPayment(testPayment.getId())).thenReturn(testPayment);

        UserPrincipal principal = new UserPrincipal(riderId, "test@example.com", "RIDER");

        mockMvc.perform(get("/v1/payments/{id}", testPayment.getId())
                .with(user(principal)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(testPayment.getId().toString()))
            .andExpect(jsonPath("$.amount").value(5000.00));
    }

    @Test
    @DisplayName("Should return 403 when rider accesses another rider's payment")
    @WithMockUser(roles = "RIDER")
    void shouldReturn403WhenRiderAccessesAnotherRidersPayment() throws Exception {
        UUID otherRiderId = UUID.randomUUID();
        testPayment.setRiderId(otherRiderId);

        when(paymentService.getPayment(testPayment.getId())).thenReturn(testPayment);

        UserPrincipal principal = new UserPrincipal(riderId, "test@example.com", "RIDER");

        mockMvc.perform(get("/v1/payments/{id}", testPayment.getId())
                .with(user(principal)))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Should allow admin to access any payment")
    @WithMockUser(roles = "ADMIN")
    void shouldAllowAdminToAccessAnyPayment() throws Exception {
        when(paymentService.getPayment(testPayment.getId())).thenReturn(testPayment);

        UserPrincipal principal = new UserPrincipal(UUID.randomUUID(), "admin@example.com", "ADMIN");

        mockMvc.perform(get("/v1/payments/{id}", testPayment.getId())
                .with(user(principal)))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should get rider's payments")
    @WithMockUser(roles = "RIDER")
    void shouldGetRidersPayments() throws Exception {
        List<Payment> payments = List.of(testPayment);
        when(paymentService.getPaymentsByRider(riderId)).thenReturn(payments);

        UserPrincipal principal = new UserPrincipal(riderId, "test@example.com", "RIDER");

        mockMvc.perform(get("/v1/payments/my-payments")
                .with(user(principal)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$[0].id").value(testPayment.getId().toString()));
    }

    @Test
    @DisplayName("Should get payment by booking ID")
    @WithMockUser(roles = "RIDER")
    void shouldGetPaymentByBookingId() throws Exception {
        when(paymentService.getPaymentByBookingId(bookingId)).thenReturn(testPayment);

        UserPrincipal principal = new UserPrincipal(riderId, "test@example.com", "RIDER");

        mockMvc.perform(get("/v1/payments/booking/{bookingId}", bookingId)
                .with(user(principal)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.bookingId").value(bookingId.toString()));
    }
}
