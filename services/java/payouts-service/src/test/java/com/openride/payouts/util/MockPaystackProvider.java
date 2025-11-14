package com.openride.payouts.util;

import com.openride.payouts.payment.PaymentProvider;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Mock implementation of PaymentProvider for testing.
 * Allows configuring responses for different scenarios.
 */
public class MockPaystackProvider implements PaymentProvider {

    private boolean shouldSucceed = true;
    private String providerReference = "PAY_" + UUID.randomUUID().toString().substring(0, 8);
    private String errorMessage = null;
    
    private Map<String, BankVerificationResult> bankVerifications = new HashMap<>();
    private Map<String, TransferResult> transferResults = new HashMap<>();

    @Override
    public BankVerificationResult verifyBankAccount(String accountNumber, String bankCode) {
        // Check if pre-configured result exists
        String key = accountNumber + ":" + bankCode;
        if (bankVerifications.containsKey(key)) {
            return bankVerifications.get(key);
        }

        // Default behavior
        if (shouldSucceed) {
            return new BankVerificationResult(true, "John Doe", "GTBank", null);
        } else {
            return new BankVerificationResult(false, null, null, 
                    errorMessage != null ? errorMessage : "Verification failed");
        }
    }

    @Override
    public TransferResult initiateBankTransfer(String recipientCode, BigDecimal amount, String reference) {
        // Check if pre-configured result exists
        if (transferResults.containsKey(reference)) {
            return transferResults.get(reference);
        }

        // Default behavior
        if (shouldSucceed) {
            return new TransferResult(true, providerReference, null);
        } else {
            return new TransferResult(false, null, 
                    errorMessage != null ? errorMessage : "Transfer failed");
        }
    }

    @Override
    public String createTransferRecipient(String accountNumber, String bankCode, String accountName) {
        if (shouldSucceed) {
            return "RCP_" + UUID.randomUUID().toString().substring(0, 12);
        } else {
            throw new RuntimeException(errorMessage != null ? errorMessage : "Failed to create recipient");
        }
    }

    // Configuration methods

    /**
     * Configure mock to succeed or fail for all operations.
     */
    public MockPaystackProvider setShouldSucceed(boolean shouldSucceed) {
        this.shouldSucceed = shouldSucceed;
        return this;
    }

    /**
     * Set the provider reference returned on successful transfer.
     */
    public MockPaystackProvider setProviderReference(String reference) {
        this.providerReference = reference;
        return this;
    }

    /**
     * Set error message returned on failure.
     */
    public MockPaystackProvider setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
        return this;
    }

    /**
     * Configure specific bank verification result.
     */
    public MockPaystackProvider configureBankVerification(
            String accountNumber, 
            String bankCode, 
            boolean success, 
            String accountName, 
            String bankName) {
        
        String key = accountNumber + ":" + bankCode;
        bankVerifications.put(key, 
                new BankVerificationResult(success, accountName, bankName, 
                        success ? null : "Verification failed"));
        return this;
    }

    /**
     * Configure specific transfer result.
     */
    public MockPaystackProvider configureTransfer(
            String reference, 
            boolean success, 
            String providerRef, 
            String error) {
        
        transferResults.put(reference, new TransferResult(success, providerRef, error));
        return this;
    }

    /**
     * Simulate verification failure for specific account.
     */
    public MockPaystackProvider failVerificationFor(String accountNumber, String bankCode, String reason) {
        configureBankVerification(accountNumber, bankCode, false, null, null);
        return this;
    }

    /**
     * Simulate transfer failure for specific reference.
     */
    public MockPaystackProvider failTransferFor(String reference, String reason) {
        configureTransfer(reference, false, null, reason);
        return this;
    }

    /**
     * Reset all configurations.
     */
    public MockPaystackProvider reset() {
        shouldSucceed = true;
        providerReference = "PAY_" + UUID.randomUUID().toString().substring(0, 8);
        errorMessage = null;
        bankVerifications.clear();
        transferResults.clear();
        return this;
    }

    /**
     * Create a new mock provider that always succeeds.
     */
    public static MockPaystackProvider alwaysSucceeds() {
        return new MockPaystackProvider().setShouldSucceed(true);
    }

    /**
     * Create a new mock provider that always fails.
     */
    public static MockPaystackProvider alwaysFails(String errorMessage) {
        return new MockPaystackProvider()
                .setShouldSucceed(false)
                .setErrorMessage(errorMessage);
    }

    /**
     * Create a new mock provider with custom configuration.
     */
    public static MockPaystackProvider withCustomBehavior() {
        return new MockPaystackProvider();
    }
}
