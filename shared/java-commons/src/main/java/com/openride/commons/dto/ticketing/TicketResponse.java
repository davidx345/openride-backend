package com.openride.commons.dto.ticketing;

import java.time.LocalDateTime;

/**
 * Response DTO returned by Ticketing Service after ticket generation.
 * Contains ticket details and QR code data.
 */
public class TicketResponse {
    
    private String ticketId;
    private String bookingId;
    private String userId;
    private String driverId;
    private String status; // ACTIVE, USED, EXPIRED, CANCELLED
    private String qrCodeData;
    private String signatureHex;
    private LocalDateTime validFrom;
    private LocalDateTime validUntil;
    private LocalDateTime issuedAt;
    private String merkleRoot;
    private Integer merklePosition;
    private Long batchId;
    private String blockchainTxHash;
    private String blockchainStatus; // PENDING, CONFIRMED, FAILED
    
    public TicketResponse() {
    }
    
    public TicketResponse(String ticketId, String bookingId, String status) {
        this.ticketId = ticketId;
        this.bookingId = bookingId;
        this.status = status;
    }
    
    // Getters and setters
    
    public String getTicketId() {
        return ticketId;
    }
    
    public void setTicketId(String ticketId) {
        this.ticketId = ticketId;
    }
    
    public String getBookingId() {
        return bookingId;
    }
    
    public void setBookingId(String bookingId) {
        this.bookingId = bookingId;
    }
    
    public String getUserId() {
        return userId;
    }
    
    public void setUserId(String userId) {
        this.userId = userId;
    }
    
    public String getDriverId() {
        return driverId;
    }
    
    public void setDriverId(String driverId) {
        this.driverId = driverId;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public String getQrCodeData() {
        return qrCodeData;
    }
    
    public void setQrCodeData(String qrCodeData) {
        this.qrCodeData = qrCodeData;
    }
    
    public String getSignatureHex() {
        return signatureHex;
    }
    
    public void setSignatureHex(String signatureHex) {
        this.signatureHex = signatureHex;
    }
    
    public LocalDateTime getValidFrom() {
        return validFrom;
    }
    
    public void setValidFrom(LocalDateTime validFrom) {
        this.validFrom = validFrom;
    }
    
    public LocalDateTime getValidUntil() {
        return validUntil;
    }
    
    public void setValidUntil(LocalDateTime validUntil) {
        this.validUntil = validUntil;
    }
    
    public LocalDateTime getIssuedAt() {
        return issuedAt;
    }
    
    public void setIssuedAt(LocalDateTime issuedAt) {
        this.issuedAt = issuedAt;
    }
    
    public String getMerkleRoot() {
        return merkleRoot;
    }
    
    public void setMerkleRoot(String merkleRoot) {
        this.merkleRoot = merkleRoot;
    }
    
    public Integer getMerklePosition() {
        return merklePosition;
    }
    
    public void setMerklePosition(Integer merklePosition) {
        this.merklePosition = merklePosition;
    }
    
    public Long getBatchId() {
        return batchId;
    }
    
    public void setBatchId(Long batchId) {
        this.batchId = batchId;
    }
    
    public String getBlockchainTxHash() {
        return blockchainTxHash;
    }
    
    public void setBlockchainTxHash(String blockchainTxHash) {
        this.blockchainTxHash = blockchainTxHash;
    }
    
    public String getBlockchainStatus() {
        return blockchainStatus;
    }
    
    public void setBlockchainStatus(String blockchainStatus) {
        this.blockchainStatus = blockchainStatus;
    }
    
    @Override
    public String toString() {
        return "TicketResponse{" +
                "ticketId='" + ticketId + '\'' +
                ", bookingId='" + bookingId + '\'' +
                ", status='" + status + '\'' +
                ", issuedAt=" + issuedAt +
                ", blockchainStatus='" + blockchainStatus + '\'' +
                '}';
    }
}
