package com.uwa.printerfarm.job;

public class RefundRequestNotFoundException extends RuntimeException {

    public RefundRequestNotFoundException(long requestId) {
        super("Refund request not found: " + requestId);
    }
}
