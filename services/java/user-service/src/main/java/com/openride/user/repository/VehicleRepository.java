package com.openride.user.repository;

import com.openride.user.entity.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Vehicle entity.
 */
@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, UUID> {

    /**
     * Finds all vehicles for a user.
     *
     * @param userId user ID
     * @return list of vehicles
     */
    List<Vehicle> findByUserId(UUID userId);

    /**
     * Finds active vehicle for a user.
     *
     * @param userId user ID
     * @return optional active vehicle
     */
    Optional<Vehicle> findByUserIdAndIsActiveTrue(UUID userId);

    /**
     * Finds vehicle by license plate.
     *
     * @param licensePlate license plate number
     * @return optional vehicle
     */
    Optional<Vehicle> findByLicensePlate(String licensePlate);

    /**
     * Checks if license plate exists.
     *
     * @param licensePlate license plate number
     * @return true if exists
     */
    boolean existsByLicensePlate(String licensePlate);

    /**
     * Deactivates all vehicles for a user.
     *
     * @param userId user ID
     */
    @Modifying
    @Query("UPDATE Vehicle v SET v.isActive = false WHERE v.user.id = :userId")
    void deactivateAllVehiclesForUser(UUID userId);

    /**
     * Counts vehicles for a user.
     *
     * @param userId user ID
     * @return count of vehicles
     */
    long countByUserId(UUID userId);
}
