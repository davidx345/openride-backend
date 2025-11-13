package com.openride.payments.model;

/**
 * Enum representing supported payment methods via Korapay.
 */
public enum PaymentMethod {
    /**
     * Payment via debit/credit card.
     */
    CARD,
    
    /**
     * Direct bank transfer.
     */
    BANK_TRANSFER,
    
    /**
     * USSD code payment (e.g., *737#).
     */
    USSD,
    
    /**
     * Mobile money payment.
     */
    MOBILE_MONEY
}
