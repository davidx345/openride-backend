package com.openride.ticketing.service;

import com.openride.ticketing.dto.TicketGenerationRequest;
import com.openride.ticketing.model.Ticket;
import com.openride.ticketing.model.TicketStatus;
import com.openride.ticketing.model.MerkleBatch;
import com.openride.ticketing.model.MerkleBatchStatus;
import com.openride.ticketing.repository.TicketRepository;
import com.openride.ticketing.repository.MerkleBatchRepository;
import com.openride.ticketing.util.CanonicalJsonUtil;
import com.openride.ticketing.util.HashUtil;
import com.openride.ticketing.util.QRCodeUtil;
import com.openride.ticketing.util.SignatureUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Core service for ticket generation and management.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TicketService {
    
    private final TicketRepository ticketRepository;
    private final MerkleBatchRepository merkleBatchRepository;
    private final KeyManagementService keyManagementService;
    private final MerkleBatchService merkleBatchService;
    
    @Value("${ticketing.ticket.validity-hours:24}")
    private Integer validityHours;
    
    /**
     * Generate a new ticket from a booking.
     *
     * @param request the ticket generation request
     * @return the generated ticket
     */
    @Transactional
    public Ticket generateTicket(TicketGenerationRequest request) {
        log.info("Generating ticket for booking: {}", request.getBookingId());
        
        // Check if ticket already exists for this booking
        if (ticketRepository.existsByBookingId(request.getBookingId())) {
            log.warn("Ticket already exists for booking: {}", request.getBookingId());
            return ticketRepository.findByBookingId(request.getBookingId())
                    .orElseThrow(() -> new IllegalStateException("Ticket exists but cannot be found"));
        }
        
        // Create ticket entity
        Ticket ticket = new Ticket();
        ticket.setBookingId(request.getBookingId());
        ticket.setRiderId(request.getRiderId());
        ticket.setDriverId(request.getDriverId());
        ticket.setRouteId(request.getRouteId());
        ticket.setTripDate(request.getTripDate());
        ticket.setSeatNumber(request.getSeatNumber());
        ticket.setPickupStop(request.getPickupStop());
        ticket.setDropoffStop(request.getDropoffStop());
        ticket.setFare(request.getFare());
        ticket.setStatus(TicketStatus.PENDING);
        
        // Calculate expiry (24 hours from trip date by default)
        LocalDateTime expiresAt = request.getTripDate().plusHours(validityHours);
        ticket.setExpiresAt(expiresAt);
        
        // Generate canonical JSON
        String canonicalJson = generateCanonicalJson(ticket);
        ticket.setCanonicalJson(canonicalJson);
        
        // Compute hash
        String hash = HashUtil.sha256(canonicalJson);
        ticket.setHash(hash);
        
        // Sign the hash
        try {
            PrivateKey privateKey = keyManagementService.getPrivateKey();
            String signature = SignatureUtil.sign(hash, privateKey);
            ticket.setSignature(signature);
            
            // Get public key for QR code
            PublicKey publicKey = keyManagementService.getPublicKey();
            String publicKeyPem = SignatureUtil.publicKeyToPemString(publicKey);
            
            // Generate QR code payload
            String qrPayload = generateQRPayload(ticket, publicKeyPem);
            String qrCode = QRCodeUtil.generateQRCode(qrPayload);
            ticket.setQrCode(qrCode);
            
        } catch (Exception e) {
            log.error("Error generating ticket signature or QR code", e);
            throw new RuntimeException("Failed to generate ticket", e);
        }
        
        // Set status to VALID
        ticket.setStatus(TicketStatus.VALID);
        ticket.setGeneratedAt(LocalDateTime.now());
        
        // Save ticket
        Ticket savedTicket = ticketRepository.save(ticket);
        log.info("Ticket generated successfully: {}", savedTicket.getId());
        
        // Add to pending Merkle batch
        merkleBatchService.addTicketToBatch(savedTicket);
        
        return savedTicket;
    }
    
    /**
     * Get ticket by ID.
     *
     * @param ticketId the ticket ID
     * @return the ticket
     */
    public Ticket getTicket(UUID ticketId) {
        return ticketRepository.findById(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("Ticket not found: " + ticketId));
    }
    
    /**
     * Get ticket by booking ID.
     *
     * @param bookingId the booking ID
     * @return the ticket
     */
    public Ticket getTicketByBookingId(UUID bookingId) {
        return ticketRepository.findByBookingId(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Ticket not found for booking: " + bookingId));
    }
    
    /**
     * Revoke a ticket.
     *
     * @param ticketId the ticket ID
     * @param reason the revocation reason
     */
    @Transactional
    public void revokeTicket(UUID ticketId, String reason) {
        Ticket ticket = getTicket(ticketId);
        
        if (ticket.getStatus() == TicketStatus.REVOKED) {
            log.warn("Ticket already revoked: {}", ticketId);
            return;
        }
        
        ticket.setStatus(TicketStatus.REVOKED);
        ticket.setRevokedAt(LocalDateTime.now());
        
        ticketRepository.save(ticket);
        log.info("Ticket revoked: {} - Reason: {}", ticketId, reason);
    }
    
    /**
     * Mark ticket as used.
     *
     * @param ticketId the ticket ID
     */
    @Transactional
    public void markTicketAsUsed(UUID ticketId) {
        Ticket ticket = getTicket(ticketId);
        ticket.markAsUsed();
        ticketRepository.save(ticket);
        log.info("Ticket marked as used: {}", ticketId);
    }
    
    /**
     * Generate canonical JSON representation of ticket for hashing.
     */
    private String generateCanonicalJson(Ticket ticket) {
        Map<String, Object> ticketData = new HashMap<>();
        ticketData.put("bookingId", ticket.getBookingId().toString());
        ticketData.put("riderId", ticket.getRiderId().toString());
        ticketData.put("driverId", ticket.getDriverId().toString());
        ticketData.put("routeId", ticket.getRouteId().toString());
        ticketData.put("tripDate", ticket.getTripDate().toString());
        ticketData.put("seatNumber", ticket.getSeatNumber());
        ticketData.put("pickupStop", ticket.getPickupStop());
        ticketData.put("dropoffStop", ticket.getDropoffStop());
        ticketData.put("fare", ticket.getFare().toString());
        
        return CanonicalJsonUtil.toCanonicalJson(ticketData);
    }
    
    /**
     * Generate QR code payload with ticket data and signature.
     */
    private String generateQRPayload(Ticket ticket, String publicKeyPem) {
        Map<String, Object> qrData = new HashMap<>();
        qrData.put("ticketId", ticket.getId().toString());
        qrData.put("bookingId", ticket.getBookingId().toString());
        qrData.put("hash", ticket.getHash());
        qrData.put("signature", ticket.getSignature());
        qrData.put("publicKey", publicKeyPem);
        qrData.put("tripDate", ticket.getTripDate().toString());
        qrData.put("seatNumber", ticket.getSeatNumber());
        
        return CanonicalJsonUtil.toCanonicalJson(qrData);
    }
}
