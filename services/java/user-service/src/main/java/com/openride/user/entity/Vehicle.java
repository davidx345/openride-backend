package com.openride.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity for storing Captain's vehicle information.
 * A Captain can have multiple vehicles but only one can be active at a time.
 */
@Entity
@Table(name = "vehicles")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Vehicle {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * Vehicle manufacturer (e.g., Toyota, Honda, Mercedes).
     */
    @Column(name = "make", length = 50, nullable = false)
    private String make;

    /**
     * Vehicle model (e.g., Camry, Civic, C-Class).
     */
    @Column(name = "model", length = 50, nullable = false)
    private String model;

    /**
     * Manufacturing year (e.g., 2020).
     */
    @Column(name = "year", nullable = false)
    private Integer year;

    /**
     * Vehicle color (e.g., Silver, Black, White).
     */
    @Column(name = "color", length = 30, nullable = false)
    private String color;

    /**
     * License plate number (e.g., ABC-123DE).
     */
    @Column(name = "license_plate", length = 20, nullable = false)
    private String licensePlate;

    /**
     * Number of available passenger seats.
     */
    @Column(name = "seats_available", nullable = false)
    @Builder.Default
    private Integer seatsAvailable = 4;

    /**
     * URL to vehicle registration document.
     */
    @Column(name = "registration_url", columnDefinition = "TEXT")
    private String registrationUrl;

    /**
     * URL to vehicle insurance document.
     */
    @Column(name = "insurance_url", columnDefinition = "TEXT")
    private String insuranceUrl;

    /**
     * URL to vehicle front photo.
     */
    @Column(name = "photo_front_url", columnDefinition = "TEXT")
    private String photoFrontUrl;

    /**
     * URL to vehicle back photo.
     */
    @Column(name = "photo_back_url", columnDefinition = "TEXT")
    private String photoBackUrl;

    /**
     * URL to vehicle interior photo.
     */
    @Column(name = "photo_interior_url", columnDefinition = "TEXT")
    private String photoInteriorUrl;

    /**
     * Whether vehicle documents have been verified.
     */
    @Column(name = "is_verified", nullable = false)
    @Builder.Default
    private Boolean isVerified = false;

    /**
     * Whether this is the currently active vehicle for the Captain.
     */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Marks vehicle as verified.
     */
    public void markAsVerified() {
        this.isVerified = true;
    }

    /**
     * Sets this vehicle as active and returns it.
     *
     * @return this vehicle
     */
    public Vehicle activate() {
        this.isActive = true;
        return this;
    }

    /**
     * Deactivates this vehicle.
     */
    public void deactivate() {
        this.isActive = false;
    }

    /**
     * Gets a display name for the vehicle.
     *
     * @return formatted vehicle name (e.g., "Toyota Camry 2020 - Silver")
     */
    public String getDisplayName() {
        return String.format("%s %s %d - %s", make, model, year, color);
    }
}
