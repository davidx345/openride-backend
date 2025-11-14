package com.openride.payouts.exception;

import java.math.BigDecimal;

/**
 * Exception thrown when wallet has insufficient balance.
 */
public class InsufficientBalanceException extends PayoutsException {

    private final BigDecimal requestedAmount;
    private final BigDecimal availableBalance;

    public InsufficientBalanceException(BigDecimal requestedAmount, BigDecimal availableBalance) {
        super(String.format("Insufficient balance. Requested: %s, Available: %s",
                requestedAmount, availableBalance));
        this.requestedAmount = requestedAmount;
        this.availableBalance = availableBalance;
    }

    public BigDecimal getRequestedAmount() {
        return requestedAmount;
    }

    public BigDecimal getAvailableBalance() {
        return availableBalance;
    }
}
