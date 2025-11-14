package com.openride.payouts.service;

import com.openride.payouts.dto.BankAccountRequest;
import com.openride.payouts.dto.BankAccountResponse;
import com.openride.payouts.exception.BankAccountNotVerifiedException;
import com.openride.payouts.exception.PayoutsException;
import com.openride.payouts.model.BankAccount;
import com.openride.payouts.repository.BankAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for managing driver bank accounts.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BankAccountService {

    private final BankAccountRepository bankAccountRepository;
    private final BankVerificationService bankVerificationService;
    private final AuditService auditService;

    /**
     * Add a new bank account for driver.
     */
    @Transactional
    public BankAccountResponse addBankAccount(UUID driverId, BankAccountRequest request) {
        log.info("Adding bank account for driver: {}", driverId);

        // Check if account already exists
        boolean exists = bankAccountRepository.existsByDriverIdAndAccountNumberAndBankCode(
                driverId, request.getAccountNumber(), request.getBankCode());
        if (exists) {
            throw new PayoutsException("Bank account already exists");
        }

        // Verify account with bank
        String accountName = bankVerificationService.verifyBankAccount(
                request.getAccountNumber(), request.getBankCode());

        // Create bank account
        BankAccount bankAccount = new BankAccount();
        bankAccount.setDriverId(driverId);
        bankAccount.setAccountNumber(request.getAccountNumber());
        bankAccount.setBankCode(request.getBankCode());
        bankAccount.setBankName(request.getBankName());
        bankAccount.setAccountName(accountName);
        bankAccount.setIsVerified(true);
        bankAccount.setVerifiedAt(LocalDateTime.now());

        // If this is the first account, make it primary
        boolean hasPrimaryAccount = bankAccountRepository.existsByDriverIdAndIsPrimaryTrue(driverId);
        if (!hasPrimaryAccount) {
            bankAccount.setIsPrimary(true);
            log.info("Setting as primary account for driver: {}", driverId);
        } else {
            bankAccount.setIsPrimary(false);
        }

        BankAccount saved = bankAccountRepository.save(bankAccount);
        
        auditService.logAuditEntry("BANK_ACCOUNT", saved.getId(), "ADD_ACCOUNT", driverId, null, null);

        log.info("Bank account added successfully: {}", saved.getId());
        return mapToResponse(saved);
    }

    /**
     * Get all bank accounts for driver.
     */
    @Transactional(readOnly = true)
    public List<BankAccountResponse> getBankAccounts(UUID driverId) {
        log.debug("Getting bank accounts for driver: {}", driverId);
        return bankAccountRepository.findByDriverId(driverId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get primary bank account for driver.
     */
    @Transactional(readOnly = true)
    public BankAccountResponse getPrimaryBankAccount(UUID driverId) {
        log.debug("Getting primary bank account for driver: {}", driverId);
        return bankAccountRepository.findByDriverIdAndIsPrimaryTrue(driverId)
                .map(this::mapToResponse)
                .orElseThrow(() -> new PayoutsException("No primary bank account found"));
    }

    /**
     * Set bank account as primary.
     */
    @Transactional
    public BankAccountResponse setPrimaryAccount(UUID driverId, UUID accountId) {
        log.info("Setting primary account: {} for driver: {}", accountId, driverId);

        BankAccount account = bankAccountRepository.findById(accountId)
                .orElseThrow(() -> new PayoutsException("Bank account not found"));

        if (!account.getDriverId().equals(driverId)) {
            throw new PayoutsException("Bank account does not belong to driver");
        }

        if (!account.getIsVerified()) {
            throw new BankAccountNotVerifiedException(accountId);
        }

        // Unset current primary (database trigger will handle this, but doing it explicitly)
        bankAccountRepository.findByDriverIdAndIsPrimaryTrue(driverId)
                .ifPresent(currentPrimary -> {
                    currentPrimary.setIsPrimary(false);
                    bankAccountRepository.save(currentPrimary);
                });

        account.markAsPrimary();
        BankAccount saved = bankAccountRepository.save(account);

        auditService.logAuditEntry("BANK_ACCOUNT", accountId, "SET_PRIMARY", driverId, null, null);

        log.info("Primary account set successfully: {}", accountId);
        return mapToResponse(saved);
    }

    /**
     * Delete bank account.
     */
    @Transactional
    public void deleteBankAccount(UUID driverId, UUID accountId) {
        log.info("Deleting bank account: {} for driver: {}", accountId, driverId);

        BankAccount account = bankAccountRepository.findById(accountId)
                .orElseThrow(() -> new PayoutsException("Bank account not found"));

        if (!account.getDriverId().equals(driverId)) {
            throw new PayoutsException("Bank account does not belong to driver");
        }

        if (account.getIsPrimary()) {
            throw new PayoutsException("Cannot delete primary bank account. Set another account as primary first.");
        }

        bankAccountRepository.delete(account);
        auditService.logAuditEntry("BANK_ACCOUNT", accountId, "DELETE_ACCOUNT", driverId, null, null);

        log.info("Bank account deleted successfully: {}", accountId);
    }

    /**
     * Check if driver has verified bank account.
     */
    @Transactional(readOnly = true)
    public boolean hasVerifiedBankAccount(UUID driverId) {
        return bankAccountRepository.hasVerifiedAccount(driverId);
    }

    private BankAccountResponse mapToResponse(BankAccount account) {
        return BankAccountResponse.builder()
                .id(account.getId())
                .driverId(account.getDriverId())
                .accountNumber(account.getMaskedAccountNumber())
                .bankCode(account.getBankCode())
                .bankName(account.getBankName())
                .accountName(account.getAccountName())
                .isPrimary(account.getIsPrimary())
                .isVerified(account.getIsVerified())
                .verifiedAt(account.getVerifiedAt())
                .createdAt(account.getCreatedAt())
                .build();
    }
}
