package com.openride.payouts.service;

import com.openride.payouts.config.FinancialConfig;
import com.openride.payouts.model.entity.DriverWallet;
import com.openride.payouts.model.entity.EarningsLedger;
import com.openride.payouts.repository.DriverWalletRepository;
import com.openride.payouts.repository.EarningsLedgerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Service for calculating and processing driver earnings.
 * Implements commission calculation and ledger entries.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EarningsService {

    private final DriverWalletRepository walletRepository;
    private final EarningsLedgerRepository ledgerRepository;
    private final FinancialConfig financialConfig;

    /**
     * Process earnings from completed trip.
     * 
     * @param driverId Driver ID
     * @param tripId Trip ID
     * @param tripPrice Total trip price
     * @return Updated wallet
     */
    @Transactional
    public DriverWallet processEarnings(UUID driverId, UUID tripId, BigDecimal tripPrice) {
        log.info("Processing earnings for driver: {}, trip: {}, price: {}",
                driverId, tripId, tripPrice);

        // Calculate driver earnings (after commission)
        BigDecimal driverEarnings = financialConfig.getCommission()
                .calculateDriverEarnings(tripPrice);
        
        BigDecimal platformCommission = financialConfig.getCommission()
                .calculatePlatformCommission(tripPrice);

        log.debug("Driver earnings: {}, Platform commission: {}",
                driverEarnings, platformCommission);

        // Get or create wallet with pessimistic lock
        DriverWallet wallet = walletRepository.findByDriverIdWithLock(driverId)
                .orElseGet(() -> createWallet(driverId));

        // Credit wallet
        wallet.creditEarnings(driverEarnings);
        wallet = walletRepository.save(wallet);

        // Create ledger entry
        EarningsLedger ledgerEntry = EarningsLedger.createEarningEntry(
                driverId,
                driverEarnings,
                wallet.getAvailableBalance(),
                tripId,
                String.format("Earnings from trip %s (₦%s - %.0f%% commission)",
                        tripId, tripPrice, platformCommission.multiply(BigDecimal.valueOf(100)))
        );
        ledgerRepository.save(ledgerEntry);

        log.info("Earnings processed successfully. New balance: {}",
                wallet.getAvailableBalance());

        return wallet;
    }

    /**
     * Create initial wallet for driver.
     * 
     * @param driverId Driver ID
     * @return Created wallet
     */
    private DriverWallet createWallet(UUID driverId) {
        log.info("Creating new wallet for driver: {}", driverId);
        
        DriverWallet wallet = DriverWallet.builder()
                .driverId(driverId)
                .availableBalance(financialConfig.getWallet().getInitialBalance())
                .build();
        
        return walletRepository.save(wallet);
    }
}
