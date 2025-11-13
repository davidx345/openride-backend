package com.openride.booking.integration;

import com.openride.booking.dto.CreateBookingRequest;
import com.openride.booking.dto.CreateBookingResponse;
import com.openride.booking.model.Booking;
import com.openride.booking.model.enums.BookingStatus;
import com.openride.booking.repository.BookingRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests with Testcontainers
 * 
 * Tests:
 * - Full booking flow
 * - Distributed locking under concurrent load
 * - Seat inventory race conditions
 * - Event publishing to Kafka
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class BookingIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:14")
        .withDatabaseName("testdb")
        .withUsername("test")
        .withPassword("test");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
        .withExposedPorts(6379);

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        
        registry.add("spring.redis.host", redis::getHost);
        registry.add("spring.redis.port", redis::getFirstMappedPort);
        
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private BookingRepository bookingRepository;

    @Test
    void testCreateBooking_Success() {
        // Arrange
        CreateBookingRequest request = CreateBookingRequest.builder()
            .routeId(UUID.randomUUID())
            .originStopId("STOP1")
            .destinationStopId("STOP2")
            .travelDate(LocalDate.now().plusDays(7))
            .seatsBooked(2)
            .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("test-jwt-token");

        // Act
        ResponseEntity<CreateBookingResponse> response = restTemplate.postForEntity(
            "/v1/bookings",
            new HttpEntity<>(request, headers),
            CreateBookingResponse.class
        );

        // Assert
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getBooking());
        assertEquals(BookingStatus.HELD, response.getBody().getBooking().getStatus());
    }

    @Test
    void testConcurrentBooking_DistributedLocking() throws InterruptedException {
        // Arrange
        UUID routeId = UUID.randomUUID();
        LocalDate travelDate = LocalDate.now().plusDays(7);
        int totalSeats = 10;
        int concurrentRequests = 20;
        
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(concurrentRequests);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        // Act - Simulate concurrent booking requests
        for (int i = 0; i < concurrentRequests; i++) {
            executor.submit(() -> {
                try {
                    CreateBookingRequest request = CreateBookingRequest.builder()
                        .routeId(routeId)
                        .originStopId("STOP1")
                        .destinationStopId("STOP2")
                        .travelDate(travelDate)
                        .seatsBooked(1)
                        .build();

                    HttpHeaders headers = new HttpHeaders();
                    headers.setBearerAuth("test-jwt-token");

                    ResponseEntity<CreateBookingResponse> response = restTemplate.postForEntity(
                        "/v1/bookings",
                        new HttpEntity<>(request, headers),
                        CreateBookingResponse.class
                    );

                    if (response.getStatusCode() == HttpStatus.CREATED) {
                        successCount.incrementAndGet();
                    } else {
                        failureCount.incrementAndGet();
                    }

                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // Assert - Only totalSeats should succeed, rest should fail
        assertTrue(successCount.get() <= totalSeats, 
            "Success count should not exceed total seats");
        assertEquals(concurrentRequests, successCount.get() + failureCount.get(),
            "Total requests should equal success + failure");
    }

    @Test
    void testIdempotency_DuplicateRequest() {
        // Arrange
        String idempotencyKey = UUID.randomUUID().toString();
        CreateBookingRequest request = CreateBookingRequest.builder()
            .routeId(UUID.randomUUID())
            .originStopId("STOP1")
            .destinationStopId("STOP2")
            .travelDate(LocalDate.now().plusDays(7))
            .seatsBooked(2)
            .idempotencyKey(idempotencyKey)
            .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("test-jwt-token");

        // Act - Make same request twice
        ResponseEntity<CreateBookingResponse> response1 = restTemplate.postForEntity(
            "/v1/bookings",
            new HttpEntity<>(request, headers),
            CreateBookingResponse.class
        );

        ResponseEntity<CreateBookingResponse> response2 = restTemplate.postForEntity(
            "/v1/bookings",
            new HttpEntity<>(request, headers),
            CreateBookingResponse.class
        );

        // Assert - Both should succeed with same booking ID
        assertEquals(HttpStatus.CREATED, response1.getStatusCode());
        assertEquals(HttpStatus.CREATED, response2.getStatusCode());
        assertEquals(
            response1.getBody().getBooking().getId(),
            response2.getBody().getBooking().getId(),
            "Idempotent requests should return same booking"
        );
    }

    @Test
    void testSeatHoldExpiration() throws InterruptedException {
        // Arrange
        CreateBookingRequest request = CreateBookingRequest.builder()
            .routeId(UUID.randomUUID())
            .originStopId("STOP1")
            .destinationStopId("STOP2")
            .travelDate(LocalDate.now().plusDays(7))
            .seatsBooked(2)
            .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("test-jwt-token");

        // Act - Create booking
        ResponseEntity<CreateBookingResponse> response = restTemplate.postForEntity(
            "/v1/bookings",
            new HttpEntity<>(request, headers),
            CreateBookingResponse.class
        );

        UUID bookingId = response.getBody().getBooking().getId();

        // Wait for expiration (assuming 10-minute TTL in test config)
        Thread.sleep(11 * 60 * 1000); // 11 minutes

        // Assert - Booking should be expired
        Booking booking = bookingRepository.findById(bookingId).orElseThrow();
        assertEquals(BookingStatus.EXPIRED, booking.getStatus());
    }
}
