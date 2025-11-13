package com.openride.ticketing.controller;

import com.openride.ticketing.dto.TicketGenerationRequest;
import com.openride.ticketing.dto.TicketResponse;
import com.openride.ticketing.dto.TicketVerificationRequest;
import com.openride.ticketing.dto.TicketVerificationResponse;
import com.openride.ticketing.model.Ticket;
import com.openride.ticketing.model.VerificationResult;
import com.openride.ticketing.service.KeyManagementService;
import com.openride.ticketing.service.TicketService;
import com.openride.ticketing.service.TicketVerificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST API controller for ticket operations.
 */
@Slf4j
@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
@Tag(name = "Ticketing", description = "Ticket generation and verification APIs")
public class TicketController {
    
    private final TicketService ticketService;
    private final TicketVerificationService verificationService;
    private final KeyManagementService keyManagementService;
    
    /**
     * Generate a new ticket (internal endpoint - called by Booking Service).
     */
    @PostMapping("/tickets")
    @Operation(
            summary = "Generate ticket",
            description = "Internal endpoint to generate a blockchain-anchored ticket for a confirmed booking",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    public ResponseEntity<TicketResponse> generateTicket(
            @Valid @RequestBody TicketGenerationRequest request) {
        
        log.info("Generating ticket for booking: {}", request.getBookingId());
        
        Ticket ticket = ticketService.generateTicket(request);
        TicketResponse response = TicketResponse.fromTicket(ticket);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    /**
     * Get ticket by booking ID.
     */
    @GetMapping("/bookings/{bookingId}/ticket")
    @Operation(
            summary = "Get ticket by booking",
            description = "Retrieve ticket details and QR code for a specific booking",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    public ResponseEntity<TicketResponse> getTicketByBooking(
            @PathVariable UUID bookingId) {
        
        log.info("Retrieving ticket for booking: {}", bookingId);
        
        Ticket ticket = ticketService.getTicketByBookingId(bookingId);
        TicketResponse response = TicketResponse.fromTicket(ticket);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get ticket by ticket ID.
     */
    @GetMapping("/tickets/{ticketId}")
    @Operation(
            summary = "Get ticket by ID",
            description = "Retrieve ticket details by ticket ID",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    public ResponseEntity<TicketResponse> getTicket(
            @PathVariable UUID ticketId) {
        
        log.info("Retrieving ticket: {}", ticketId);
        
        Ticket ticket = ticketService.getTicket(ticketId);
        TicketResponse response = TicketResponse.fromTicket(ticket);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Verify a ticket (used by drivers for check-in).
     */
    @PostMapping("/tickets/verify")
    @Operation(
            summary = "Verify ticket",
            description = "Verify ticket authenticity using signature or Merkle proof",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    public ResponseEntity<TicketVerificationResponse> verifyTicket(
            @Valid @RequestBody TicketVerificationRequest request,
            HttpServletRequest httpRequest) {
        
        log.info("Verifying ticket: {}", request.getTicketId());
        
        String ipAddress = getClientIpAddress(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");
        
        VerificationResult result;
        
        if (request.isUseMerkleProof()) {
            result = verificationService.verifyTicketWithMerkleProof(
                    request.getTicketId(),
                    request.getVerifierId(),
                    ipAddress,
                    userAgent
            );
        } else {
            result = verificationService.verifyTicket(
                    request.getTicketId(),
                    request.getVerifierId(),
                    ipAddress,
                    userAgent
            );
        }
        
        TicketVerificationResponse response = new TicketVerificationResponse(
                request.getTicketId(),
                result,
                result == VerificationResult.VALID,
                getResultMessage(result)
        );
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Mark ticket as used (called after successful verification and check-in).
     */
    @PostMapping("/tickets/{ticketId}/use")
    @Operation(
            summary = "Mark ticket as used",
            description = "Mark ticket as used after passenger check-in",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    public ResponseEntity<Void> useTicket(@PathVariable UUID ticketId) {
        log.info("Marking ticket as used: {}", ticketId);
        
        ticketService.markTicketAsUsed(ticketId);
        
        return ResponseEntity.ok().build();
    }
    
    /**
     * Revoke a ticket (admin or driver).
     */
    @PostMapping("/tickets/{ticketId}/revoke")
    @Operation(
            summary = "Revoke ticket",
            description = "Revoke a ticket (admin or driver only)",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    public ResponseEntity<Void> revokeTicket(
            @PathVariable UUID ticketId,
            @RequestParam String reason) {
        
        log.info("Revoking ticket: {} - Reason: {}", ticketId, reason);
        
        ticketService.revokeTicket(ticketId, reason);
        
        return ResponseEntity.ok().build();
    }
    
    /**
     * Get public key for offline ticket verification.
     */
    @GetMapping("/tickets/public-key")
    @Operation(
            summary = "Get public key",
            description = "Get public key in PEM format for offline ticket signature verification"
    )
    public ResponseEntity<PublicKeyResponse> getPublicKey() {
        log.debug("Retrieving public key");
        
        String publicKeyPem = keyManagementService.getPublicKeyPem();
        PublicKeyResponse response = new PublicKeyResponse(publicKeyPem);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Extract client IP address from request.
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
    
    /**
     * Get human-readable message for verification result.
     */
    private String getResultMessage(VerificationResult result) {
        return switch (result) {
            case VALID -> "Ticket is valid and can be used";
            case INVALID -> "Ticket signature or proof is invalid";
            case EXPIRED -> "Ticket has expired";
            case REVOKED -> "Ticket has been revoked";
            case NOT_FOUND -> "Ticket not found";
        };
    }
    
    /**
     * Public key response DTO.
     */
    public record PublicKeyResponse(String publicKey) {}
}
