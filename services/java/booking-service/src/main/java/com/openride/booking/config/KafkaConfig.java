package com.openride.booking.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka configuration for event publishing
 * 
 * Topics:
 * - booking.created: New booking created
 * - booking.confirmed: Payment confirmed, booking finalized
 * - booking.cancelled: Booking cancelled
 * - booking.completed: Trip completed successfully
 */
@Configuration
public class KafkaConfig {

    @Value("${kafka.topics.booking-created}")
    private String bookingCreatedTopic;

    @Value("${kafka.topics.booking-confirmed}")
    private String bookingConfirmedTopic;

    @Value("${kafka.topics.booking-cancelled}")
    private String bookingCancelledTopic;

    @Value("${kafka.topics.booking-completed}")
    private String bookingCompletedTopic;

    @Bean
    public NewTopic bookingCreatedTopic() {
        return TopicBuilder.name(bookingCreatedTopic)
            .partitions(3)
            .replicas(1)
            .build();
    }

    @Bean
    public NewTopic bookingConfirmedTopic() {
        return TopicBuilder.name(bookingConfirmedTopic)
            .partitions(3)
            .replicas(1)
            .build();
    }

    @Bean
    public NewTopic bookingCancelledTopic() {
        return TopicBuilder.name(bookingCancelledTopic)
            .partitions(3)
            .replicas(1)
            .build();
    }

    @Bean
    public NewTopic bookingCompletedTopic() {
        return TopicBuilder.name(bookingCompletedTopic)
            .partitions(3)
            .replicas(1)
            .build();
    }
}
