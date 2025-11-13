package com.openride.commons.dto.ticketing;

/**
 * Request DTO for verifying a ticket.
 * Sent from Driver Service to Ticketing Service during ride pickup.
 */
public class TicketVerificationRequest {
    
    private String ticketId;
    private String qrCodeData;
    private String signature;
    private String driverId;
    private String deviceId;
    private Double latitude;
    private Double longitude;
    
    public TicketVerificationRequest() {
    }
    
    public TicketVerificationRequest(String ticketId, String driverId) {
        this.ticketId = ticketId;
        this.driverId = driverId;
    }
    
    // Getters and setters
    
    public String getTicketId() {
        return ticketId;
    }
    
    public void setTicketId(String ticketId) {
        this.ticketId = ticketId;
    }
    
    public String getQrCodeData() {
        return qrCodeData;
    }
    
    public void setQrCodeData(String qrCodeData) {
        this.qrCodeData = qrCodeData;
    }
    
    public String getSignature() {
        return signature;
    }
    
    public void setSignature(String signature) {
        this.signature = signature;
    }
    
    public String getDriverId() {
        return driverId;
    }
    
    public void setDriverId(String driverId) {
        this.driverId = driverId;
    }
    
    public String getDeviceId() {
        return deviceId;
    }
    
    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }
    
    public Double getLatitude() {
        return latitude;
    }
    
    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }
    
    public Double getLongitude() {
        return longitude;
    }
    
    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }
    
    @Override
    public String toString() {
        return "TicketVerificationRequest{" +
                "ticketId='" + ticketId + '\'' +
                ", driverId='" + driverId + '\'' +
                ", deviceId='" + deviceId + '\'' +
                '}';
    }
}
