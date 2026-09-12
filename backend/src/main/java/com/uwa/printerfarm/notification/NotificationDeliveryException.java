package com.uwa.printerfarm.notification;

public class NotificationDeliveryException extends RuntimeException {

    public NotificationDeliveryException(String ownerUniId, Throwable cause) {
        super("Failed to send job notification email to " + ownerUniId, cause);
    }
}
