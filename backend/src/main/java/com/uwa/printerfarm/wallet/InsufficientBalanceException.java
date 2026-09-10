package com.uwa.printerfarm.wallet;

import java.math.BigDecimal;

public class InsufficientBalanceException extends RuntimeException {

    public InsufficientBalanceException(String ownerUniId, BigDecimal available, BigDecimal required) {
        super("User " + ownerUniId + " has insufficient balance: available=" + available + ", required=" + required);
    }
}
