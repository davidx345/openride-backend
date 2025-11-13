package com.openride.booking.mapper;

import com.openride.booking.dto.BookingDTO;
import com.openride.booking.model.Booking;
import org.springframework.stereotype.Component;

/**
 * Mapper for converting between Booking entity and DTOs
 */
@Component
public class BookingMapper {

    /**
     * Convert Booking entity to BookingDTO
     * 
     * @param booking Booking entity
     * @return BookingDTO
     */
    public BookingDTO toDTO(Booking booking) {
        if (booking == null) {
            return null;
        }

        return BookingDTO.builder()
            .id(booking.getId())
            .bookingReference(booking.getBookingReference())
            .riderId(booking.getRiderId())
            .driverId(booking.getDriverId())
            .routeId(booking.getRouteId())
            .originStop(BookingDTO.StopDTO.builder()
                .id(booking.getOriginStopId())
                .build())
            .destinationStop(BookingDTO.StopDTO.builder()
                .id(booking.getDestinationStopId())
                .build())
            .travelDate(booking.getTravelDate())
            .departureTime(booking.getDepartureTime())
            .seatsBooked(booking.getSeatsBooked())
            .seatNumbers(booking.getSeatNumbers())
            .pricePerSeat(booking.getPricePerSeat())
            .totalPrice(booking.getTotalPrice())
            .platformFee(booking.getPlatformFee())
            .status(booking.getStatus())
            .paymentId(booking.getPaymentId())
            .paymentStatus(booking.getPaymentStatus())
            .cancellationReason(booking.getCancellationReason())
            .cancelledAt(booking.getCancelledAt())
            .refundAmount(booking.getRefundAmount())
            .refundStatus(booking.getRefundStatus())
            .bookingSource(booking.getBookingSource())
            .createdAt(booking.getCreatedAt())
            .updatedAt(booking.getUpdatedAt())
            .expiresAt(booking.getExpiresAt())
            .confirmedAt(booking.getConfirmedAt())
            .build();
    }

    /**
     * Convert Booking entity to enriched BookingDTO with route/stop details
     * 
     * @param booking Booking entity
     * @param routeName Route name from driver service
     * @return Enriched BookingDTO
     */
    public BookingDTO toEnrichedDTO(Booking booking, String routeName) {
        BookingDTO dto = toDTO(booking);
        if (dto != null) {
            dto.setRouteName(routeName);
        }
        return dto;
    }
}
