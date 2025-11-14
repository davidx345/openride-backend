package com.openride.payouts.integration;

import java.math.BigDecimal;

/**
 * Interface for payment provider integration (Paystack, Flutterwave, etc).
 */
public interface PaymentProvider {

    /**
     * Verify bank account with provider.
     *
     * @param accountNumber Bank account number
     * @param bankCode Bank code
     * @return Account holder name
     */
    String verifyBankAccount(String accountNumber, String bankCode);

    /**
     * Initiate bank transfer.
     *
     * @param accountNumber Destination account number
     * @param bankCode Bank code
     * @param amount Amount to transfer (in Naira)
     * @param narration Transfer description
     * @return Provider transaction reference
     */
    String initiateBankTransfer(String accountNumber, String bankCode, BigDecimal amount, String narration);

    /**
     * Get provider name.
     *
     * @return Provider name (e.g., "PAYSTACK", "FLUTTERWAVE")
     */
    String getProviderName();
}
