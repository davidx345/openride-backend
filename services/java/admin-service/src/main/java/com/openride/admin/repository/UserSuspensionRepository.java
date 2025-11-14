package com.openride.admin.repository;

import com.openride.admin.model.UserSuspension;
import com.openride.admin.model.UserSuspension.SuspensionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for UserSuspension entity.
 */
@Repository
public interface UserSuspensionRepository extends JpaRepository<UserSuspension, UUID> {

    /**
     * Find all suspensions for a user.
     */
    List<UserSuspension> findByUserIdOrderByStartDateDesc(UUID userId);

    /**
     * Find active suspension for a user.
     */
    @Query("SELECT s FROM UserSuspension s WHERE s.userId = :userId " +
           "AND s.isActive = true " +
           "AND s.startDate <= :now " +
           "AND (s.suspensionType = 'PERMANENT' OR s.endDate > :now) " +
           "ORDER BY s.startDate DESC")
    Optional<UserSuspension> findActiveSuspensionForUser(
        @Param("userId") UUID userId,
        @Param("now") Instant now
    );

    /**
     * Find all active suspensions.
     */
    @Query("SELECT s FROM UserSuspension s WHERE s.isActive = true " +
           "AND s.startDate <= :now " +
           "AND (s.suspensionType = 'PERMANENT' OR s.endDate > :now)")
    Page<UserSuspension> findAllActiveSuspensions(
        @Param("now") Instant now,
        Pageable pageable
    );

    /**
     * Find suspensions by type.
     */
    Page<UserSuspension> findBySuspensionTypeAndIsActiveOrderByStartDateDesc(
        SuspensionType suspensionType,
        Boolean isActive,
        Pageable pageable
    );

    /**
     * Count active suspensions.
     */
    @Query("SELECT COUNT(s) FROM UserSuspension s WHERE s.isActive = true " +
           "AND s.startDate <= :now " +
           "AND (s.suspensionType = 'PERMANENT' OR s.endDate > :now)")
    long countActiveSuspensions(@Param("now") Instant now);

    /**
     * Find expired temporary suspensions that need deactivation.
     */
    @Query("SELECT s FROM UserSuspension s WHERE s.isActive = true " +
           "AND s.suspensionType = 'TEMPORARY' " +
           "AND s.endDate < :now")
    List<UserSuspension> findExpiredSuspensions(@Param("now") Instant now);
}
