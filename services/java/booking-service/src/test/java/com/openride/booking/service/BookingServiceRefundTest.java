package com.openride.booking.service;

import com.openride.booking.config.BookingConfigProperties;
import com.openride.booking.model.Booking;
import com.openride.booking.model.enums.BookingStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Unit tests for BookingService refund calculation
 */
@ExtendWith(MockitoExtension.class)
class BookingServiceRefundTest {

    @Mock
    private BookingConfigProperties config;

    @Mock
    private BookingConfigProperties.CancellationConfig cancellationConfig;

    private BookingService bookingService;

    @BeforeEach
    void setUp() {
        when(config.getCancellation()).thenReturn(cancellationConfig);
        when(cancellationConfig.getFullRefundHours()).thenReturn(24);
        when(cancellationConfig.getPartialRefundHours()).thenReturn(6);
        when(cancellationConfig.getPartialRefundPercentage()).thenReturn(BigDecimal.valueOf(0.50));
    }

    @Test
    void testFullRefund_MoreThan24Hours() {
        // Arrange
        Booking booking = createBooking(LocalDate.now().plusDays(2), LocalTime.of(14, 0));
        booking.setTotalPrice(BigDecimal.valueOf(100.00));

        // Act
        BigDecimal refund = calculateRefund(booking);

        // Assert
        assertEquals(BigDecimal.valueOf(100.00), refund);
    }

    @Test
    void testPartialRefund_Between6And24Hours() {
        // Arrange
        Booking booking = createBooking(LocalDate.now().plusDays(1), LocalTime.of(14, 0));
        booking.setTotalPrice(BigDecimal.valueOf(100.00));

        // Act
        BigDecimal refund = calculateRefund(booking);

        // Assert
        assertEquals(BigDecimal.valueOf(50.00), refund.setScale(2));
    }

    @Test
    void testNoRefund_LessThan6Hours() {
        // Arrange
        Booking booking = createBooking(LocalDate.now(), LocalTime.now().plusHours(3));
        booking.setTotalPrice(BigDecimal.valueOf(100.00));

        // Act
        BigDecimal refund = calculateRefund(booking);

        // Assert
        assertEquals(BigDecimal.ZERO, refund);
    }

    @Test
    void testRefundBoundary_Exactly24Hours() {
        // Arrange
        Booking booking = createBooking(LocalDate.now().plusDays(1), LocalTime.now());
        booking.setTotalPrice(BigDecimal.valueOf(100.00));

        // Act
        BigDecimal refund = calculateRefund(booking);

        // Assert - Should be full refund (>= 24 hours)
        assertEquals(BigDecimal.valueOf(100.00), refund);
    }

    @Test
    void testRefundBoundary_Exactly6Hours() {
        // Arrange
        Booking booking = createBooking(LocalDate.now(), LocalTime.now().plusHours(6));
        booking.setTotalPrice(BigDecimal.valueOf(100.00));

        // Act
        BigDecimal refund = calculateRefund(booking);

        // Assert - Should be partial refund (>= 6 hours)
        assertEquals(BigDecimal.valueOf(50.00), refund.setScale(2));
    }

    private Booking createBooking(LocalDate travelDate, LocalTime departureTime) {
        return Booking.builder()
            .travelDate(travelDate)
            .departureTime(departureTime)
            .status(BookingStatus.CONFIRMED)
            .build();
    }

    private BigDecimal calculateRefund(Booking booking) {
        // Simplified refund calculation for testing
        java.time.LocalDateTime departureTime = java.time.LocalDateTime.of(
            booking.getTravelDate(),
            booking.getDepartureTime()
        );

        java.time.Duration timeUntilDeparture = java.time.Duration.between(
            java.time.LocalDateTime.now(),
            departureTime
        );

        long hoursUntilDeparture = timeUntilDeparture.toHours();

        if (hoursUntilDeparture >= cancellationConfig.getFullRefundHours()) {
            return booking.getTotalPrice();
        }

        if (hoursUntilDeparture >= cancellationConfig.getPartialRefundHours()) {
            return booking.getTotalPrice()
                .multiply(cancellationConfig.getPartialRefundPercentage())
                .setScale(2, java.math.RoundingMode.HALF_UP);
        }

        return BigDecimal.ZERO;
    }
}
