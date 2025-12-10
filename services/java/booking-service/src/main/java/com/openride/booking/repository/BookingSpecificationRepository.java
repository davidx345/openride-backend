package com.openride.booking.repository;

import com.openride.booking.model.Booking;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * Extended repository interface for Booking with Specification support.
 * This enables dynamic query building for admin search functionality.
 */
@Repository
public interface BookingSpecificationRepository extends JpaSpecificationExecutor<Booking> {
}
