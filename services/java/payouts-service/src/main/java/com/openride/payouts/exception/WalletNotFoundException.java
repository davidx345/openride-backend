package com.openride.payouts.exception;

/**
 * Exception thrown when wallet is not found.
 */
public class WalletNotFoundException extends PayoutsException {

    public WalletNotFoundException(String message) {
        super(message);
    }

    public static WalletNotFoundException forDriver(java.util.UUID driverId) {
        return new WalletNotFoundException("Wallet not found for driver: " + driverId);
    }

    public static WalletNotFoundException forWallet(java.util.UUID walletId) {
        return new WalletNotFoundException("Wallet not found: " + walletId);
    }
}
