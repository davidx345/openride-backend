package com.openride.payouts.repository;

import com.openride.payouts.model.entity.DriverWallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for DriverWallet entity operations.
 */
@Repository
public interface DriverWalletRepository extends JpaRepository<DriverWallet, UUID> {

    /**
     * Find wallet by driver ID.
     * 
     * @param driverId Driver ID
     * @return Optional wallet
     */
    Optional<DriverWallet> findByDriverId(UUID driverId);

    /**
     * Find wallet by driver ID with pessimistic write lock.
     * Use for balance updates to prevent race conditions.
     * 
     * @param driverId Driver ID
     * @return Optional wallet
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM DriverWallet w WHERE w.driverId = :driverId")
    Optional<DriverWallet> findByDriverIdWithLock(@Param("driverId") UUID driverId);

    /**
     * Check if wallet exists for driver.
     * 
     * @param driverId Driver ID
     * @return true if wallet exists
     */
    boolean existsByDriverId(UUID driverId);

    /**
     * Get total platform wallet balance (sum of all available balances).
     * 
     * @return Total balance across all wallets
     */
    @Query("SELECT COALESCE(SUM(w.availableBalance), 0) FROM DriverWallet w")
    BigDecimal getTotalPlatformBalance();

    /**
     * Get total pending payouts across all wallets.
     * 
     * @return Total pending payout amount
     */
    @Query("SELECT COALESCE(SUM(w.pendingPayout), 0) FROM DriverWallet w")
    BigDecimal getTotalPendingPayouts();
}
