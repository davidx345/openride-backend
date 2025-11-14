package com.openride.payouts.service;

import com.openride.payouts.dto.EarningsSummaryResponse;
import com.openride.payouts.dto.LedgerEntryResponse;
import com.openride.payouts.dto.WalletResponse;
import com.openride.payouts.exception.WalletNotFoundException;
import com.openride.payouts.model.DriverWallet;
import com.openride.payouts.model.EarningsLedger;
import com.openride.payouts.model.enums.TransactionType;
import com.openride.payouts.repository.DriverWalletRepository;
import com.openride.payouts.repository.EarningsLedgerRepository;
import com.openride.payouts.repository.PayoutRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Service for managing driver wallets.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WalletService {

    private final DriverWalletRepository walletRepository;
    private final EarningsLedgerRepository ledgerRepository;
    private final PayoutRequestRepository payoutRequestRepository;

    /**
     * Get wallet by driver ID.
     */
    @Transactional(readOnly = true)
    public WalletResponse getWallet(UUID driverId) {
        log.debug("Getting wallet for driver: {}", driverId);
        DriverWallet wallet = walletRepository.findByDriverId(driverId)
                .orElseThrow(() -> WalletNotFoundException.forDriver(driverId));
        return mapToWalletResponse(wallet);
    }

    /**
     * Get or create wallet for driver.
     */
    @Transactional
    public WalletResponse getOrCreateWallet(UUID driverId) {
        log.debug("Getting or creating wallet for driver: {}", driverId);
        DriverWallet wallet = walletRepository.findByDriverId(driverId)
                .orElseGet(() -> {
                    log.info("Creating new wallet for driver: {}", driverId);
                    DriverWallet newWallet = new DriverWallet();
                    newWallet.setDriverId(driverId);
                    newWallet.setAvailableBalance(BigDecimal.ZERO);
                    newWallet.setPendingPayout(BigDecimal.ZERO);
                    newWallet.setTotalEarnings(BigDecimal.ZERO);
                    newWallet.setTotalPaidOut(BigDecimal.ZERO);
                    newWallet.setLifetimeEarnings(BigDecimal.ZERO);
                    newWallet.setVersion(0L);
                    return walletRepository.save(newWallet);
                });
        return mapToWalletResponse(wallet);
    }

    /**
     * Get transaction history for driver.
     */
    @Transactional(readOnly = true)
    public Page<LedgerEntryResponse> getTransactionHistory(UUID driverId, Pageable pageable) {
        log.debug("Getting transaction history for driver: {}", driverId);
        
        // Verify wallet exists
        walletRepository.findByDriverId(driverId)
                .orElseThrow(() -> WalletNotFoundException.forDriver(driverId));

        return ledgerRepository.findByDriverId(driverId, pageable)
                .map(this::mapToLedgerEntryResponse);
    }

    /**
     * Get earnings summary for driver.
     */
    @Transactional(readOnly = true)
    public EarningsSummaryResponse getEarningsSummary(UUID driverId) {
        log.debug("Getting earnings summary for driver: {}", driverId);
        
        DriverWallet wallet = walletRepository.findByDriverId(driverId)
                .orElseThrow(() -> WalletNotFoundException.forDriver(driverId));

        // Get last payout date
        LocalDateTime lastPayoutAt = payoutRequestRepository
                .findTopByDriverIdOrderByCreatedAtDesc(driverId)
                .map(payout -> payout.getCreatedAt())
                .orElse(null);

        // Get last earning date
        LocalDateTime lastEarningAt = ledgerRepository
                .findTopByDriverIdAndTransactionTypeOrderByCreatedAtDesc(driverId, TransactionType.EARNING)
                .map(EarningsLedger::getCreatedAt)
                .orElse(null);

        // Get total trips (count of earning entries)
        Long totalTrips = ledgerRepository.countByDriverIdAndTransactionType(driverId, TransactionType.EARNING);

        return EarningsSummaryResponse.builder()
                .availableBalance(wallet.getAvailableBalance())
                .pendingPayout(wallet.getPendingPayout())
                .totalEarnings(wallet.getTotalEarnings())
                .totalPaidOut(wallet.getTotalPaidOut())
                .lifetimeEarnings(wallet.getLifetimeEarnings())
                .totalTrips(totalTrips.intValue())
                .lastPayoutAt(lastPayoutAt)
                .lastEarningAt(lastEarningAt)
                .build();
    }

    /**
     * Get wallet balance for driver.
     */
    @Transactional(readOnly = true)
    public BigDecimal getAvailableBalance(UUID driverId) {
        log.debug("Getting available balance for driver: {}", driverId);
        return walletRepository.findByDriverId(driverId)
                .map(DriverWallet::getAvailableBalance)
                .orElseThrow(() -> WalletNotFoundException.forDriver(driverId));
    }

    private WalletResponse mapToWalletResponse(DriverWallet wallet) {
        return WalletResponse.builder()
                .id(wallet.getId())
                .driverId(wallet.getDriverId())
                .availableBalance(wallet.getAvailableBalance())
                .pendingPayout(wallet.getPendingPayout())
                .totalEarnings(wallet.getTotalEarnings())
                .totalPaidOut(wallet.getTotalPaidOut())
                .lifetimeEarnings(wallet.getLifetimeEarnings())
                .lastEarningAt(wallet.getLastEarningAt())
                .createdAt(wallet.getCreatedAt())
                .build();
    }

    private LedgerEntryResponse mapToLedgerEntryResponse(EarningsLedger entry) {
        return LedgerEntryResponse.builder()
                .id(entry.getId())
                .walletId(entry.getWalletId())
                .driverId(entry.getDriverId())
                .amount(entry.getAmount())
                .balanceAfter(entry.getBalanceAfter())
                .entryType(entry.getEntryType())
                .transactionType(entry.getTransactionType())
                .referenceId(entry.getReferenceId())
                .description(entry.getDescription())
                .createdAt(entry.getCreatedAt())
                .build();
    }
}
