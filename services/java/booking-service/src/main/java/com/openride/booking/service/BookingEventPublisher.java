package com.openride.booking.service;

import com.openride.booking.event.BookingCancelledEvent;
import com.openride.booking.event.BookingCompletedEvent;
import com.openride.booking.event.BookingConfirmedEvent;
import com.openride.booking.event.BookingCreatedEvent;
import com.openride.booking.model.Booking;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Service for publishing booking events to Kafka
 * 
 * Events:
 * - booking.created: When booking is created
 * - booking.confirmed: When booking is paid and confirmed
 * - booking.cancelled: When booking is cancelled
 * - booking.completed: When trip is completed
 */
@Slf4j
@Service
public class BookingEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${kafka.topics.booking-created}")
    private String bookingCreatedTopic;

    @Value("${kafka.topics.booking-confirmed}")
    private String bookingConfirmedTopic;

    @Value("${kafka.topics.booking-cancelled}")
    private String bookingCancelledTopic;

    @Value("${kafka.topics.booking-completed}")
    private String bookingCompletedTopic;

    public BookingEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Publish booking created event
     * 
     * @param booking Created booking
     */
    @Async
    public void publishBookingCreated(Booking booking) {
        BookingCreatedEvent event = BookingCreatedEvent.builder()
            .bookingId(booking.getId())
            .bookingReference(booking.getBookingReference())
            .riderId(booking.getRiderId())
            .routeId(booking.getRouteId())
            .travelDate(booking.getTravelDate())
            .departureTime(booking.getDepartureTime())
            .pickupLocationId(booking.getPickupLocationId())
            .dropoffLocationId(booking.getDropoffLocationId())
            .numberOfSeats(booking.getNumberOfSeats())
            .seatNumbers(booking.getSeatNumbers())
            .baseFare(booking.getBaseFare())
            .platformFee(booking.getPlatformFee())
            .totalAmount(booking.getTotalAmount())
            .source(booking.getSource())
            .expiresAt(booking.getExpiresAt())
            .createdAt(booking.getCreatedAt())
            .eventId(UUID.randomUUID().toString())
            .eventTimestamp(Instant.now())
            .build();

        publishEvent(bookingCreatedTopic, booking.getId().toString(), event);
    }

    /**
     * Publish booking confirmed event
     * 
     * @param booking Confirmed booking
     */
    @Async
    public void publishBookingConfirmed(Booking booking) {
        BookingConfirmedEvent event = BookingConfirmedEvent.builder()
            .bookingId(booking.getId())
            .bookingReference(booking.getBookingReference())
            .riderId(booking.getRiderId())
            .routeId(booking.getRouteId())
            .travelDate(booking.getTravelDate())
            .departureTime(booking.getDepartureTime())
            .pickupLocationId(booking.getPickupLocationId())
            .dropoffLocationId(booking.getDropoffLocationId())
            .numberOfSeats(booking.getNumberOfSeats())
            .seatNumbers(booking.getSeatNumbers())
            .totalAmount(booking.getTotalAmount())
            .paymentId(booking.getPaymentId())
            .confirmedAt(booking.getConfirmedAt())
            .eventId(UUID.randomUUID().toString())
            .eventTimestamp(Instant.now())
            .build();

        publishEvent(bookingConfirmedTopic, booking.getId().toString(), event);
    }

    /**
     * Publish booking cancelled event
     * 
     * @param booking Cancelled booking
     */
    @Async
    public void publishBookingCancelled(Booking booking) {
        BookingCancelledEvent event = BookingCancelledEvent.builder()
            .bookingId(booking.getId())
            .bookingReference(booking.getBookingReference())
            .riderId(booking.getRiderId())
            .routeId(booking.getRouteId())
            .numberOfSeats(booking.getNumberOfSeats())
            .seatNumbers(booking.getSeatNumbers())
            .totalAmount(booking.getTotalAmount())
            .refundAmount(booking.getRefundAmount())
            .cancellationReason(booking.getCancellationReason())
            .cancelledAt(booking.getCancelledAt())
            .eventId(UUID.randomUUID().toString())
            .eventTimestamp(Instant.now())
            .build();

        publishEvent(bookingCancelledTopic, booking.getId().toString(), event);
    }

    /**
     * Publish booking completed event
     * 
     * @param booking Completed booking
     */
    @Async
    public void publishBookingCompleted(Booking booking) {
        BookingCompletedEvent event = BookingCompletedEvent.builder()
            .bookingId(booking.getId())
            .bookingReference(booking.getBookingReference())
            .riderId(booking.getRiderId())
            .routeId(booking.getRouteId())
            .totalAmount(booking.getTotalAmount())
            .completedAt(booking.getCompletedAt())
            .eventId(UUID.randomUUID().toString())
            .eventTimestamp(Instant.now())
            .build();

        publishEvent(bookingCompletedTopic, booking.getId().toString(), event);
    }

    /**
     * Publish event to Kafka topic
     * 
     * @param topic Kafka topic
     * @param key Message key (booking ID)
     * @param event Event payload
     */
    private void publishEvent(String topic, String key, Object event) {
        try {
            CompletableFuture<SendResult<String, Object>> future = 
                kafkaTemplate.send(topic, key, event);

            future.whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to publish event to topic {}: {}", 
                        topic, ex.getMessage());
                } else {
                    log.info("Successfully published event to topic {}, partition {}, offset {}", 
                        topic,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
                }
            });

        } catch (Exception e) {
            log.error("Error publishing event to topic {}: {}", topic, e.getMessage(), e);
        }
    }
}
