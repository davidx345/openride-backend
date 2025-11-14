package com.openride.payouts.service;

import com.openride.payouts.integration.PaymentProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service for verifying bank accounts with payment providers.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BankVerificationService {

    private final PaymentProvider paymentProvider;

    /**
     * Verify bank account with payment provider.
     *
     * @param accountNumber The bank account number
     * @param bankCode The bank code
     * @return The account holder name
     */
    public String verifyBankAccount(String accountNumber, String bankCode) {
        log.info("Verifying bank account: {}, bank code: {}", maskAccountNumber(accountNumber), bankCode);

        try {
            String accountName = paymentProvider.verifyBankAccount(accountNumber, bankCode);
            log.info("Bank account verified successfully: {}", accountName);
            return accountName;
        } catch (Exception e) {
            log.error("Bank account verification failed: account={}, bank={}", 
                    maskAccountNumber(accountNumber), bankCode, e);
            throw new RuntimeException("Bank account verification failed: " + e.getMessage(), e);
        }
    }

    private String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.length() < 4) {
            return "****";
        }
        return "******" + accountNumber.substring(accountNumber.length() - 4);
    }
}
