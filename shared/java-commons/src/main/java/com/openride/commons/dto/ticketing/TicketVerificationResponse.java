package com.openride.commons.dto.ticketing;

import java.time.LocalDateTime;

/**
 * Response DTO returned after ticket verification.
 * Indicates whether ticket is valid and can be used for the ride.
 */
public class TicketVerificationResponse {
    
    private boolean valid;
    private String ticketId;
    private String bookingId;
    private String userId;
    private String userName;
    private String status;
    private String validationMessage;
    private LocalDateTime validFrom;
    private LocalDateTime validUntil;
    private LocalDateTime verifiedAt;
    private boolean blockchainVerified;
    private String merkleProof;
    
    public TicketVerificationResponse() {
    }
    
    public TicketVerificationResponse(boolean valid, String validationMessage) {
        this.valid = valid;
        this.validationMessage = validationMessage;
    }
    
    // Getters and setters
    
    public boolean isValid() {
        return valid;
    }
    
    public void setValid(boolean valid) {
        this.valid = valid;
    }
    
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
    
    public String getUserName() {
        return userName;
    }
    
    public void setUserName(String userName) {
        this.userName = userName;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public String getValidationMessage() {
        return validationMessage;
    }
    
    public void setValidationMessage(String validationMessage) {
        this.validationMessage = validationMessage;
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
    
    public LocalDateTime getVerifiedAt() {
        return verifiedAt;
    }
    
    public void setVerifiedAt(LocalDateTime verifiedAt) {
        this.verifiedAt = verifiedAt;
    }
    
    public boolean isBlockchainVerified() {
        return blockchainVerified;
    }
    
    public void setBlockchainVerified(boolean blockchainVerified) {
        this.blockchainVerified = blockchainVerified;
    }
    
    public String getMerkleProof() {
        return merkleProof;
    }
    
    public void setMerkleProof(String merkleProof) {
        this.merkleProof = merkleProof;
    }
    
    @Override
    public String toString() {
        return "TicketVerificationResponse{" +
                "valid=" + valid +
                ", ticketId='" + ticketId + '\'' +
                ", status='" + status + '\'' +
                ", validationMessage='" + validationMessage + '\'' +
                ", blockchainVerified=" + blockchainVerified +
                '}';
    }
}
