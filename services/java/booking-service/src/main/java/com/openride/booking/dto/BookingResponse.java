package com.openride.booking.dto;

import com.openride.booking.model.Booking;
import com.openride.booking.model.enums.BookingStatus;
import com.openride.booking.model.enums.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingResponse {
    private UUID id;
    private String bookingReference;
    private String riderId;
    private String driverId;
    private String routeId;
    private BookingStatus status;
    private PaymentStatus paymentStatus;
    private BigDecimal totalPrice;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static BookingResponse fromEntity(Booking booking) {
        return BookingResponse.builder()
                .id(booking.getId())
                .bookingReference(booking.getBookingReference())
                .riderId(booking.getRiderId().toString())
                .driverId(booking.getDriverId().toString())
                .routeId(booking.getRouteId().toString())
                .status(booking.getStatus())
                .paymentStatus(booking.getPaymentStatus())
                .totalPrice(booking.getTotalPrice())
                .createdAt(LocalDateTime.ofInstant(booking.getCreatedAt(), java.time.ZoneId.of("UTC")))
                .updatedAt(LocalDateTime.ofInstant(booking.getUpdatedAt(), java.time.ZoneId.of("UTC")))
                .build();
    }
}
