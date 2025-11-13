package com.openride.ticketing.dto;

import com.openride.ticketing.model.Ticket;
import com.openride.ticketing.model.TicketStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Ticket response DTO.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TicketResponse {
    
    private UUID id;
    private UUID bookingId;
    private UUID riderId;
    private UUID driverId;
    private UUID routeId;
    
    private LocalDateTime tripDate;
    private Integer seatNumber;
    private String pickupStop;
    private String dropoffStop;
    private BigDecimal fare;
    
    private String hash;
    private String signature;
    private String qrCode;
    
    private TicketStatus status;
    private LocalDateTime generatedAt;
    private LocalDateTime expiresAt;
    private LocalDateTime usedAt;
    private LocalDateTime revokedAt;
    
    private MerkleBatchInfo merkleBatch;
    
    /**
     * Create response from ticket entity.
     */
    public static TicketResponse fromTicket(Ticket ticket) {
        TicketResponse response = new TicketResponse();
        response.setId(ticket.getId());
        response.setBookingId(ticket.getBookingId());
        response.setRiderId(ticket.getRiderId());
        response.setDriverId(ticket.getDriverId());
        response.setRouteId(ticket.getRouteId());
        response.setTripDate(ticket.getTripDate());
        response.setSeatNumber(ticket.getSeatNumber());
        response.setPickupStop(ticket.getPickupStop());
        response.setDropoffStop(ticket.getDropoffStop());
        response.setFare(ticket.getFare());
        response.setHash(ticket.getHash());
        response.setSignature(ticket.getSignature());
        response.setQrCode(ticket.getQrCode());
        response.setStatus(ticket.getStatus());
        response.setGeneratedAt(ticket.getGeneratedAt());
        response.setExpiresAt(ticket.getExpiresAt());
        response.setUsedAt(ticket.getUsedAt());
        response.setRevokedAt(ticket.getRevokedAt());
        
        // Include Merkle batch info if available
        if (ticket.getMerkleBatch() != null) {
            MerkleBatchInfo batchInfo = new MerkleBatchInfo();
            batchInfo.setBatchId(ticket.getMerkleBatch().getId());
            batchInfo.setMerkleRoot(ticket.getMerkleBatch().getMerkleRoot());
            batchInfo.setStatus(ticket.getMerkleBatch().getStatus().name());
            
            if (ticket.getMerkleBatch().getBlockchainAnchor() != null) {
                batchInfo.setTransactionHash(ticket.getMerkleBatch().getBlockchainAnchor().getTransactionHash());
                batchInfo.setBlockchainType(ticket.getMerkleBatch().getBlockchainAnchor().getBlockchainType().name());
            }
            
            response.setMerkleBatch(batchInfo);
        }
        
        return response;
    }
    
    /**
     * Merkle batch information.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MerkleBatchInfo {
        private UUID batchId;
        private String merkleRoot;
        private String status;
        private String transactionHash;
        private String blockchainType;
    }
}
