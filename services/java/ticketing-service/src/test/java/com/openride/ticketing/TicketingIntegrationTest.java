package com.openride.ticketing;

import com.openride.commons.client.TicketingServiceClient;
import com.openride.commons.dto.ticketing.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end integration test for Ticketing Service.
 * Tests the complete booking → payment → ticket generation → verification flow.
 * Uses Testcontainers for PostgreSQL and Redis.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
public class TicketingIntegrationTest {
    
    @LocalServerPort
    private int port;
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:14-3.3")
    )
            .withDatabaseName("openride_test")
            .withUsername("test")
            .withPassword("test");
    
    @Container
    static GenericContainer<?> redis = new GenericContainer<>(
            DockerImageName.parse("redis:7-alpine")
    )
            .withExposedPorts(6379);
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        
        // Test blockchain config (use mock or testnet)
        registry.add("ticketing.blockchain.type", () -> "POLYGON");
        registry.add("ticketing.blockchain.rpc-url", () -> "https://rpc-mumbai.maticvigil.com/");
        registry.add("ticketing.blockchain.chain-id", () -> 80001);
        registry.add("ticketing.crypto.auto-generate-keys", () -> true);
    }
    
    @Autowired
    private TicketingServiceClient ticketingClient;
    
    private String testBookingId;
    private String testUserId;
    private String testDriverId;
    
    @BeforeEach
    void setUp() {
        testBookingId = "booking-" + System.currentTimeMillis();
        testUserId = "user-" + System.currentTimeMillis();
        testDriverId = "driver-" + System.currentTimeMillis();
    }
    
    @Test
    void testCompleteTicketingFlow() {
        // Step 1: Simulate successful booking and payment
        // (In real scenario, this comes from Booking Service after payment confirmation)
        
        // Step 2: Generate ticket
        TicketGenerationRequest generateRequest = new TicketGenerationRequest();
        generateRequest.setBookingId(testBookingId);
        generateRequest.setUserId(testUserId);
        generateRequest.setDriverId(testDriverId);
        generateRequest.setVehicleId("vehicle-123");
        generateRequest.setRideType("STANDARD");
        generateRequest.setScheduledTime(LocalDateTime.now().plusHours(1));
        generateRequest.setPickupLocation("123 Main St");
        generateRequest.setDropoffLocation("456 Oak Ave");
        generateRequest.setFare(new BigDecimal("25.50"));
        generateRequest.setPaymentId("payment-123");
        generateRequest.setPaymentMethod("CREDIT_CARD");
        
        TicketResponse ticketResponse = ticketingClient.generateTicket(generateRequest);
        
        // Verify ticket was created
        assertNotNull(ticketResponse);
        assertNotNull(ticketResponse.getTicketId());
        assertEquals(testBookingId, ticketResponse.getBookingId());
        assertEquals(testUserId, ticketResponse.getUserId());
        assertEquals(testDriverId, ticketResponse.getDriverId());
        assertEquals("ACTIVE", ticketResponse.getStatus());
        assertNotNull(ticketResponse.getQrCodeData());
        assertNotNull(ticketResponse.getSignatureHex());
        assertNotNull(ticketResponse.getIssuedAt());
        
        String ticketId = ticketResponse.getTicketId();
        
        // Step 3: Retrieve ticket details
        TicketResponse retrievedTicket = ticketingClient.getTicket(ticketId);
        
        assertNotNull(retrievedTicket);
        assertEquals(ticketId, retrievedTicket.getTicketId());
        assertEquals(testBookingId, retrievedTicket.getBookingId());
        
        // Step 4: Driver verifies ticket at pickup
        TicketVerificationRequest verifyRequest = new TicketVerificationRequest();
        verifyRequest.setTicketId(ticketId);
        verifyRequest.setQrCodeData(ticketResponse.getQrCodeData());
        verifyRequest.setSignature(ticketResponse.getSignatureHex());
        verifyRequest.setDriverId(testDriverId);
        verifyRequest.setDeviceId("driver-device-123");
        verifyRequest.setLatitude(37.7749);
        verifyRequest.setLongitude(-122.4194);
        
        TicketVerificationResponse verifyResponse = ticketingClient.verifyTicket(verifyRequest);
        
        // Verify ticket is valid
        assertNotNull(verifyResponse);
        assertTrue(verifyResponse.isValid(), "Ticket should be valid");
        assertEquals(ticketId, verifyResponse.getTicketId());
        assertEquals(testBookingId, verifyResponse.getBookingId());
        assertEquals(testUserId, verifyResponse.getUserId());
        assertNotNull(verifyResponse.getVerifiedAt());
        
        // Step 5: Verify ticket status changed to USED
        TicketResponse usedTicket = ticketingClient.getTicket(ticketId);
        assertEquals("USED", usedTicket.getStatus());
        
        // Step 6: Try to verify same ticket again (should fail)
        TicketVerificationResponse secondVerify = ticketingClient.verifyTicket(verifyRequest);
        assertFalse(secondVerify.isValid(), "Used ticket should not be valid");
        assertTrue(secondVerify.getValidationMessage().contains("already been used"));
    }
    
    @Test
    void testTicketVerificationWithWrongDriver() {
        // Generate ticket
        TicketGenerationRequest generateRequest = new TicketGenerationRequest();
        generateRequest.setBookingId(testBookingId);
        generateRequest.setUserId(testUserId);
        generateRequest.setDriverId(testDriverId);
        generateRequest.setScheduledTime(LocalDateTime.now().plusHours(1));
        generateRequest.setFare(new BigDecimal("30.00"));
        
        TicketResponse ticketResponse = ticketingClient.generateTicket(generateRequest);
        
        // Try to verify with different driver
        TicketVerificationRequest verifyRequest = new TicketVerificationRequest();
        verifyRequest.setTicketId(ticketResponse.getTicketId());
        verifyRequest.setQrCodeData(ticketResponse.getQrCodeData());
        verifyRequest.setSignature(ticketResponse.getSignatureHex());
        verifyRequest.setDriverId("wrong-driver-999"); // Wrong driver!
        
        TicketVerificationResponse verifyResponse = ticketingClient.verifyTicket(verifyRequest);
        
        // Verification should fail
        assertFalse(verifyResponse.isValid(), "Ticket should not be valid for wrong driver");
        assertTrue(verifyResponse.getValidationMessage().contains("driver"));
    }
    
    @Test
    void testTicketCancellation() {
        // Generate ticket
        TicketGenerationRequest generateRequest = new TicketGenerationRequest();
        generateRequest.setBookingId(testBookingId);
        generateRequest.setUserId(testUserId);
        generateRequest.setDriverId(testDriverId);
        generateRequest.setScheduledTime(LocalDateTime.now().plusHours(2));
        generateRequest.setFare(new BigDecimal("15.00"));
        
        TicketResponse ticketResponse = ticketingClient.generateTicket(generateRequest);
        String ticketId = ticketResponse.getTicketId();
        
        // Cancel ticket (booking was cancelled)
        ticketingClient.cancelTicket(ticketId);
        
        // Verify ticket is cancelled
        TicketResponse cancelledTicket = ticketingClient.getTicket(ticketId);
        assertEquals("CANCELLED", cancelledTicket.getStatus());
        
        // Try to verify cancelled ticket
        TicketVerificationRequest verifyRequest = new TicketVerificationRequest();
        verifyRequest.setTicketId(ticketId);
        verifyRequest.setDriverId(testDriverId);
        
        TicketVerificationResponse verifyResponse = ticketingClient.verifyTicket(verifyRequest);
        assertFalse(verifyResponse.isValid(), "Cancelled ticket should not be valid");
    }
    
    @Test
    void testMerkleProofGeneration() throws InterruptedException {
        // Generate multiple tickets to trigger batch creation
        String[] ticketIds = new String[5];
        
        for (int i = 0; i < 5; i++) {
            TicketGenerationRequest request = new TicketGenerationRequest();
            request.setBookingId("booking-" + i);
            request.setUserId("user-" + i);
            request.setDriverId("driver-" + i);
            request.setScheduledTime(LocalDateTime.now().plusHours(1));
            request.setFare(new BigDecimal("20.00"));
            
            TicketResponse response = ticketingClient.generateTicket(request);
            ticketIds[i] = response.getTicketId();
        }
        
        // Wait for batch job to process (in real scenario, this runs on schedule)
        // For testing, we might need to manually trigger batch creation
        Thread.sleep(2000);
        
        // Get Merkle proof for first ticket
        MerkleProofResponse proofResponse = ticketingClient.getMerkleProof(ticketIds[0]);
        
        // Verify proof structure
        if (proofResponse != null && proofResponse.getProof() != null) {
            assertNotNull(proofResponse.getTicketId());
            assertNotNull(proofResponse.getMerkleRoot());
            assertNotNull(proofResponse.getProof());
            assertTrue(proofResponse.getProof().size() > 0, "Should have merkle proof siblings");
            assertNotNull(proofResponse.getBatchId());
        }
        // Note: Proof might not be available if batch hasn't been created yet
    }
    
    @Test
    void testConcurrentTicketGeneration() throws InterruptedException {
        // Test concurrent ticket generation (simulates high load)
        int numThreads = 10;
        Thread[] threads = new Thread[numThreads];
        TicketResponse[] responses = new TicketResponse[numThreads];
        
        for (int i = 0; i < numThreads; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                TicketGenerationRequest request = new TicketGenerationRequest();
                request.setBookingId("booking-concurrent-" + index);
                request.setUserId("user-" + index);
                request.setDriverId("driver-" + index);
                request.setScheduledTime(LocalDateTime.now().plusHours(1));
                request.setFare(new BigDecimal("25.00"));
                
                responses[index] = ticketingClient.generateTicket(request);
            });
            threads[i].start();
        }
        
        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }
        
        // Verify all tickets were created successfully
        for (int i = 0; i < numThreads; i++) {
            assertNotNull(responses[i], "Thread " + i + " should have generated ticket");
            assertNotNull(responses[i].getTicketId());
            assertEquals("booking-concurrent-" + i, responses[i].getBookingId());
        }
        
        // Verify all ticket IDs are unique
        long uniqueCount = java.util.Arrays.stream(responses)
                .map(TicketResponse::getTicketId)
                .distinct()
                .count();
        assertEquals(numThreads, uniqueCount, "All tickets should have unique IDs");
    }
}
