package com.openride.payouts.kafka;

import com.openride.payouts.dto.TripCompletedEvent;
import com.openride.payouts.service.EarningsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for trip completed events.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TripCompletedConsumer {

    private final EarningsService earningsService;

    @KafkaListener(
            topics = "${kafka.topics.trip-completed}",
            groupId = "${kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeTripCompleted(
            @Payload TripCompletedEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment
    ) {
        log.info("Received trip completed event: tripId={}, driverId={}, totalPrice={}, partition={}, offset={}",
                event.getTripId(), event.getDriverId(), event.getTotalPrice(), partition, offset);

        try {
            // Process earnings for the driver
            earningsService.processEarnings(
                    event.getDriverId(),
                    event.getTripId(),
                    event.getTotalPrice()
            );

            // Manually commit offset after successful processing
            acknowledgment.acknowledge();
            
            log.info("Trip completed event processed successfully: tripId={}", event.getTripId());
            
        } catch (Exception e) {
            log.error("Error processing trip completed event: tripId={}, error={}",
                    event.getTripId(), e.getMessage(), e);
            
            // Don't acknowledge - message will be retried or sent to DLQ
            // based on Kafka consumer configuration
            throw new RuntimeException("Failed to process trip completed event", e);
        }
    }
}
