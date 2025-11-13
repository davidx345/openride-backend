package com.openride.commons.dto.ticketing;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Request DTO for generating a new ticket after successful booking payment.
 * Sent from Booking Service to Ticketing Service.
 */
public class TicketGenerationRequest {
    
    private String bookingId;
    private String userId;
    private String driverId;
    private String vehicleId;
    private String rideType; // STANDARD, POOL, PREMIUM
    private LocalDateTime scheduledTime;
    private String pickupLocation;
    private String dropoffLocation;
    private BigDecimal fare;
    private String paymentId;
    private String paymentMethod;
    
    public TicketGenerationRequest() {
    }
    
    public TicketGenerationRequest(String bookingId, String userId, String driverId) {
        this.bookingId = bookingId;
        this.userId = userId;
        this.driverId = driverId;
    }
    
    // Getters and setters
    
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
    
    public String getVehicleId() {
        return vehicleId;
    }
    
    public void setVehicleId(String vehicleId) {
        this.vehicleId = vehicleId;
    }
    
    public String getRideType() {
        return rideType;
    }
    
    public void setRideType(String rideType) {
        this.rideType = rideType;
    }
    
    public LocalDateTime getScheduledTime() {
        return scheduledTime;
    }
    
    public void setScheduledTime(LocalDateTime scheduledTime) {
        this.scheduledTime = scheduledTime;
    }
    
    public String getPickupLocation() {
        return pickupLocation;
    }
    
    public void setPickupLocation(String pickupLocation) {
        this.pickupLocation = pickupLocation;
    }
    
    public String getDropoffLocation() {
        return dropoffLocation;
    }
    
    public void setDropoffLocation(String dropoffLocation) {
        this.dropoffLocation = dropoffLocation;
    }
    
    public BigDecimal getFare() {
        return fare;
    }
    
    public void setFare(BigDecimal fare) {
        this.fare = fare;
    }
    
    public String getPaymentId() {
        return paymentId;
    }
    
    public void setPaymentId(String paymentId) {
        this.paymentId = paymentId;
    }
    
    public String getPaymentMethod() {
        return paymentMethod;
    }
    
    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }
    
    @Override
    public String toString() {
        return "TicketGenerationRequest{" +
                "bookingId='" + bookingId + '\'' +
                ", userId='" + userId + '\'' +
                ", driverId='" + driverId + '\'' +
                ", rideType='" + rideType + '\'' +
                ", scheduledTime=" + scheduledTime +
                ", fare=" + fare +
                '}';
    }
}
