package com.openride.payouts.integration;

import com.openride.payouts.kafka.PayoutEventProducer;
import com.openride.payouts.kafka.TripCompletedConsumer;
import com.openride.payouts.model.dto.TripCompletedEvent;
import com.openride.payouts.model.entity.DriverWallet;
import com.openride.payouts.model.entity.PayoutRequest;
import com.openride.payouts.model.enums.PayoutStatus;
import com.openride.payouts.repository.DriverWalletRepository;
import com.openride.payouts.repository.PayoutRequestRepository;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Integration test for Kafka consumer and producer.
 */
@SpringBootTest
@Testcontainers
class KafkaIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:14-alpine"))
            .withDatabaseName("payouts_test")
            .withUsername("test")
            .withPassword("test");

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    private TripCompletedConsumer tripCompletedConsumer;

    @Autowired
    private PayoutEventProducer payoutEventProducer;

    @Autowired
    private DriverWalletRepository walletRepository;

    @Autowired
    private PayoutRequestRepository payoutRequestRepository;

    private UUID driverId;

    @BeforeEach
    void setUp() {
        driverId = UUID.randomUUID();
        
        // Clean up
        payoutRequestRepository.deleteAll();
        walletRepository.deleteAll();
    }

    @Test
    void tripCompletedConsumer_ShouldProcessEventAndCreditWallet() {
        // Arrange
        TripCompletedEvent event = new TripCompletedEvent();
        event.setTripId(UUID.randomUUID());
        event.setDriverId(driverId);
        event.setTotalPrice(BigDecimal.valueOf(10000.00));
        event.setCompletedAt(LocalDateTime.now());

        // Act
        tripCompletedConsumer.consumeTripCompleted(event, null);

        // Assert
        await().atMost(5, SECONDS).untilAsserted(() -> {
            DriverWallet wallet = walletRepository.findByDriverId(driverId).orElseThrow();
            
            assertThat(wallet.getAvailableBalance()).isEqualByComparingTo(BigDecimal.valueOf(8500.00));
            assertThat(wallet.getTotalEarnings()).isEqualByComparingTo(BigDecimal.valueOf(8500.00));
            assertThat(wallet.getLastEarningAt()).isNotNull();
        });
    }

    @Test
    void tripCompletedConsumer_WithMultipleEvents_ShouldProcessSequentially() {
        // Arrange
        TripCompletedEvent event1 = createTripEvent(BigDecimal.valueOf(5000.00));
        TripCompletedEvent event2 = createTripEvent(BigDecimal.valueOf(7000.00));
        TripCompletedEvent event3 = createTripEvent(BigDecimal.valueOf(3000.00));

        // Act
        tripCompletedConsumer.consumeTripCompleted(event1, null);
        tripCompletedConsumer.consumeTripCompleted(event2, null);
        tripCompletedConsumer.consumeTripCompleted(event3, null);

        // Assert
        await().atMost(10, SECONDS).untilAsserted(() -> {
            DriverWallet wallet = walletRepository.findByDriverId(driverId).orElseThrow();
            
            // Total: (5000 + 7000 + 3000) * 0.85 = 12,750
            assertThat(wallet.getAvailableBalance()).isEqualByComparingTo(BigDecimal.valueOf(12750.00));
            assertThat(wallet.getTotalEarnings()).isEqualByComparingTo(BigDecimal.valueOf(12750.00));
        });
    }

    @Test
    void payoutEventProducer_ShouldPublishPayoutRequested() {
        // Arrange
        Consumer<String, Object> consumer = createKafkaConsumer("payout.requested");
        
        PayoutRequest payout = createPayoutRequest();
        payout.setStatus(PayoutStatus.PENDING);

        // Act
        payoutEventProducer.publishPayoutRequested(payout);

        // Assert
        ConsumerRecords<String, Object> records = consumer.poll(Duration.ofSeconds(10));
        assertThat(records.count()).isGreaterThan(0);
        
        consumer.close();
    }

    @Test
    void payoutEventProducer_ShouldPublishPayoutApproved() {
        // Arrange
        Consumer<String, Object> consumer = createKafkaConsumer("payout.approved");
        
        PayoutRequest payout = createPayoutRequest();
        payout.setStatus(PayoutStatus.APPROVED);
        payout.setReviewedBy(UUID.randomUUID());
        payout.setReviewedAt(LocalDateTime.now());

        // Act
        payoutEventProducer.publishPayoutApproved(payout);

        // Assert
        ConsumerRecords<String, Object> records = consumer.poll(Duration.ofSeconds(10));
        assertThat(records.count()).isGreaterThan(0);
        
        consumer.close();
    }

    @Test
    void payoutEventProducer_ShouldPublishPayoutRejected() {
        // Arrange
        Consumer<String, Object> consumer = createKafkaConsumer("payout.rejected");
        
        PayoutRequest payout = createPayoutRequest();
        payout.setStatus(PayoutStatus.REJECTED);
        payout.setReviewedBy(UUID.randomUUID());
        payout.setReviewNotes("Failed verification");

        // Act
        payoutEventProducer.publishPayoutRejected(payout);

        // Assert
        ConsumerRecords<String, Object> records = consumer.poll(Duration.ofSeconds(10));
        assertThat(records.count()).isGreaterThan(0);
        
        consumer.close();
    }

    @Test
    void payoutEventProducer_ShouldPublishPayoutCompleted() {
        // Arrange
        Consumer<String, Object> consumer = createKafkaConsumer("payout.completed");
        
        PayoutRequest payout = createPayoutRequest();
        payout.setStatus(PayoutStatus.COMPLETED);
        payout.setProviderReference("PAY_123456");
        payout.setCompletedAt(LocalDateTime.now());

        // Act
        payoutEventProducer.publishPayoutCompleted(payout);

        // Assert
        ConsumerRecords<String, Object> records = consumer.poll(Duration.ofSeconds(10));
        assertThat(records.count()).isGreaterThan(0);
        
        consumer.close();
    }

    @Test
    void payoutEventProducer_ShouldPublishPayoutFailed() {
        // Arrange
        Consumer<String, Object> consumer = createKafkaConsumer("payout.failed");
        
        PayoutRequest payout = createPayoutRequest();
        payout.setStatus(PayoutStatus.FAILED);
        String failureReason = "Bank transfer failed";

        // Act
        payoutEventProducer.publishPayoutFailed(payout, failureReason);

        // Assert
        ConsumerRecords<String, Object> records = consumer.poll(Duration.ofSeconds(10));
        assertThat(records.count()).isGreaterThan(0);
        
        consumer.close();
    }

    @Test
    void tripCompletedConsumer_WithInvalidData_ShouldHandleGracefully() {
        // Arrange - Event with zero amount
        TripCompletedEvent event = new TripCompletedEvent();
        event.setTripId(UUID.randomUUID());
        event.setDriverId(driverId);
        event.setTotalPrice(BigDecimal.ZERO);
        event.setCompletedAt(LocalDateTime.now());

        // Act - Should not throw exception
        tripCompletedConsumer.consumeTripCompleted(event, null);

        // Assert - Wallet might be created but balance should be zero
        await().atMost(5, SECONDS).untilAsserted(() -> {
            DriverWallet wallet = walletRepository.findByDriverId(driverId).orElse(null);
            if (wallet != null) {
                assertThat(wallet.getAvailableBalance()).isEqualByComparingTo(BigDecimal.ZERO);
            }
        });
    }

    private TripCompletedEvent createTripEvent(BigDecimal amount) {
        TripCompletedEvent event = new TripCompletedEvent();
        event.setTripId(UUID.randomUUID());
        event.setDriverId(driverId);
        event.setTotalPrice(amount);
        event.setCompletedAt(LocalDateTime.now());
        return event;
    }

    private PayoutRequest createPayoutRequest() {
        PayoutRequest payout = new PayoutRequest();
        payout.setId(UUID.randomUUID());
        payout.setDriverId(driverId);
        payout.setWalletId(UUID.randomUUID());
        payout.setBankAccountId(UUID.randomUUID());
        payout.setAmount(BigDecimal.valueOf(10000.00));
        payout.setRequestedAt(LocalDateTime.now());
        return payout;
    }

    private Consumer<String, Object> createKafkaConsumer(String topic) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-consumer-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");

        DefaultKafkaConsumerFactory<String, Object> consumerFactory = 
                new DefaultKafkaConsumerFactory<>(props);
        
        Consumer<String, Object> consumer = consumerFactory.createConsumer();
        consumer.subscribe(Collections.singletonList(topic));
        
        return consumer;
    }
}
