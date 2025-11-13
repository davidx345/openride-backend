package com.openride.commons.client;

import com.openride.commons.dto.ticketing.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

/**
 * REST client for communicating with Ticketing Service.
 * Used by Booking Service to generate tickets after successful payment.
 */
@Component
public class TicketingServiceClient {
    
    private static final Logger logger = LoggerFactory.getLogger(TicketingServiceClient.class);
    
    private final RestTemplate restTemplate;
    private final String ticketingServiceUrl;
    
    public TicketingServiceClient(
            RestTemplate restTemplate,
            @Value("${ticketing.service.url:http://localhost:8086}") String ticketingServiceUrl) {
        this.restTemplate = restTemplate;
        this.ticketingServiceUrl = ticketingServiceUrl;
    }
    
    /**
     * Generate a new ticket for a confirmed booking.
     *
     * @param request Ticket generation request with booking details
     * @return TicketResponse with ticket ID, QR code, and blockchain info
     * @throws TicketingServiceException if ticket generation fails
     */
    public TicketResponse generateTicket(TicketGenerationRequest request) {
        try {
            logger.info("Generating ticket for booking: {}", request.getBookingId());
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<TicketGenerationRequest> entity = new HttpEntity<>(request, headers);
            
            ResponseEntity<TicketResponse> response = restTemplate.exchange(
                    ticketingServiceUrl + "/api/v1/tickets",
                    HttpMethod.POST,
                    entity,
                    TicketResponse.class
            );
            
            TicketResponse ticketResponse = response.getBody();
            logger.info("Ticket generated successfully: {}", ticketResponse.getTicketId());
            
            return ticketResponse;
            
        } catch (HttpClientErrorException e) {
            logger.error("Client error generating ticket: {}", e.getMessage());
            throw new TicketingServiceException("Failed to generate ticket: " + e.getMessage(), e);
        } catch (HttpServerErrorException e) {
            logger.error("Server error generating ticket: {}", e.getMessage());
            throw new TicketingServiceException("Ticketing service unavailable: " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Unexpected error generating ticket", e);
            throw new TicketingServiceException("Unexpected error generating ticket", e);
        }
    }
    
    /**
     * Get ticket details by ticket ID.
     *
     * @param ticketId Unique ticket identifier
     * @return TicketResponse with full ticket details
     * @throws TicketingServiceException if ticket not found or service error
     */
    public TicketResponse getTicket(String ticketId) {
        try {
            logger.debug("Fetching ticket: {}", ticketId);
            
            ResponseEntity<TicketResponse> response = restTemplate.getForEntity(
                    ticketingServiceUrl + "/api/v1/tickets/" + ticketId,
                    TicketResponse.class
            );
            
            return response.getBody();
            
        } catch (HttpClientErrorException e) {
            logger.error("Ticket not found: {}", ticketId);
            throw new TicketingServiceException("Ticket not found: " + ticketId, e);
        } catch (Exception e) {
            logger.error("Error fetching ticket", e);
            throw new TicketingServiceException("Error fetching ticket", e);
        }
    }
    
    /**
     * Verify a ticket (called by driver during pickup).
     *
     * @param request Verification request with ticket ID and driver info
     * @return TicketVerificationResponse indicating if ticket is valid
     * @throws TicketingServiceException if verification fails
     */
    public TicketVerificationResponse verifyTicket(TicketVerificationRequest request) {
        try {
            logger.info("Verifying ticket: {} by driver: {}", request.getTicketId(), request.getDriverId());
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<TicketVerificationRequest> entity = new HttpEntity<>(request, headers);
            
            ResponseEntity<TicketVerificationResponse> response = restTemplate.exchange(
                    ticketingServiceUrl + "/api/v1/tickets/verify",
                    HttpMethod.POST,
                    entity,
                    TicketVerificationResponse.class
            );
            
            TicketVerificationResponse verificationResponse = response.getBody();
            logger.info("Ticket verification result: {}", verificationResponse.isValid());
            
            return verificationResponse;
            
        } catch (Exception e) {
            logger.error("Error verifying ticket", e);
            throw new TicketingServiceException("Error verifying ticket", e);
        }
    }
    
    /**
     * Get Merkle proof for blockchain verification.
     *
     * @param ticketId Unique ticket identifier
     * @return MerkleProofResponse with proof data
     * @throws TicketingServiceException if proof not available
     */
    public MerkleProofResponse getMerkleProof(String ticketId) {
        try {
            logger.debug("Fetching Merkle proof for ticket: {}", ticketId);
            
            ResponseEntity<MerkleProofResponse> response = restTemplate.getForEntity(
                    ticketingServiceUrl + "/api/v1/tickets/" + ticketId + "/proof",
                    MerkleProofResponse.class
            );
            
            return response.getBody();
            
        } catch (HttpClientErrorException e) {
            logger.error("Merkle proof not found for ticket: {}", ticketId);
            throw new TicketingServiceException("Merkle proof not available", e);
        } catch (Exception e) {
            logger.error("Error fetching Merkle proof", e);
            throw new TicketingServiceException("Error fetching Merkle proof", e);
        }
    }
    
    /**
     * Cancel a ticket (if booking is cancelled before ride).
     *
     * @param ticketId Unique ticket identifier
     * @throws TicketingServiceException if cancellation fails
     */
    public void cancelTicket(String ticketId) {
        try {
            logger.info("Cancelling ticket: {}", ticketId);
            
            restTemplate.delete(ticketingServiceUrl + "/api/v1/tickets/" + ticketId);
            
            logger.info("Ticket cancelled successfully: {}", ticketId);
            
        } catch (Exception e) {
            logger.error("Error cancelling ticket", e);
            throw new TicketingServiceException("Error cancelling ticket", e);
        }
    }
    
    /**
     * Custom exception for ticketing service communication errors.
     */
    public static class TicketingServiceException extends RuntimeException {
        public TicketingServiceException(String message) {
            super(message);
        }
        
        public TicketingServiceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
