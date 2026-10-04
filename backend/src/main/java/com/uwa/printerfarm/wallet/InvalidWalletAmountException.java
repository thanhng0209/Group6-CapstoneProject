package com.uwa.printerfarm.wallet;

import java.math.BigDecimal;

/**
 * Thrown when a wallet debit or credit is requested with a non-positive
 * amount. Debiting a negative amount would silently increase a balance
 * instead of decreasing it (and vice versa for credit), so this must be
 * rejected before any balance mutation happens.
 */
public class InvalidWalletAmountException extends RuntimeException {

    public InvalidWalletAmountException(BigDecimal amount) {
        super("Wallet amount must be positive, but was: " + amount);
    }
}
