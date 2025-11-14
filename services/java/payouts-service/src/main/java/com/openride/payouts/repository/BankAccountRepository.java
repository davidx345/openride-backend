package com.openride.payouts.repository;

import com.openride.payouts.model.entity.BankAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for BankAccount entity operations.
 */
@Repository
public interface BankAccountRepository extends JpaRepository<BankAccount, UUID> {

    /**
     * Find all bank accounts for a driver.
     * 
     * @param driverId Driver ID
     * @return List of bank accounts
     */
    List<BankAccount> findByDriverId(UUID driverId);

    /**
     * Find primary bank account for a driver.
     * 
     * @param driverId Driver ID
     * @return Optional bank account
     */
    Optional<BankAccount> findByDriverIdAndIsPrimaryTrue(UUID driverId);

    /**
     * Find verified bank accounts for a driver.
     * 
     * @param driverId Driver ID
     * @return List of verified bank accounts
     */
    List<BankAccount> findByDriverIdAndIsVerifiedTrue(UUID driverId);

    /**
     * Check if account number exists for driver.
     * 
     * @param driverId Driver ID
     * @param accountNumber Account number
     * @param bankCode Bank code
     * @return true if exists
     */
    boolean existsByDriverIdAndAccountNumberAndBankCode(
            UUID driverId, String accountNumber, String bankCode);

    /**
     * Find bank account by driver, account number, and bank code.
     * 
     * @param driverId Driver ID
     * @param accountNumber Account number
     * @param bankCode Bank code
     * @return Optional bank account
     */
    Optional<BankAccount> findByDriverIdAndAccountNumberAndBankCode(
            UUID driverId, String accountNumber, String bankCode);

    /**
     * Count bank accounts for driver.
     * 
     * @param driverId Driver ID
     * @return Count of bank accounts
     */
    long countByDriverId(UUID driverId);

    /**
     * Check if driver has any verified bank account.
     * 
     * @param driverId Driver ID
     * @return true if has verified account
     */
    @Query("SELECT COUNT(b) > 0 FROM BankAccount b " +
           "WHERE b.driverId = :driverId AND b.isVerified = true")
    boolean hasVerifiedAccount(@Param("driverId") UUID driverId);
}
