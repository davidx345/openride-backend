package com.openride.payouts.exception;

/**
 * Exception thrown when bank account not verified.
 */
public class BankAccountNotVerifiedException extends PayoutsException {

    public BankAccountNotVerifiedException() {
        super("Bank account must be verified before requesting payout");
    }
}
