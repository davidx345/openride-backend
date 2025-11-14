package com.openride.payouts.exception;

/**
 * Exception thrown when minimum payout amount not met.
 */
public class MinimumPayoutAmountException extends PayoutsException {

    public MinimumPayoutAmountException(java.math.BigDecimal minimumAmount) {
        super("Payout amount must be at least " + minimumAmount);
    }
}
