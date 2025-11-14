package com.openride.payouts.exception;

/**
 * Exception thrown when driver already has pending payout.
 */
public class PendingPayoutExistsException extends PayoutsException {

    public PendingPayoutExistsException() {
        super("Driver already has a pending payout request");
    }
}
