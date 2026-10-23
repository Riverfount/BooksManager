package com.riverfount.booksmanager.shared.application.port.out;

/** A {@link Notification} that could not be delivered, told to a caller that asked to know (RNF19). */
public class NotificationDeliveryException extends RuntimeException {

    public NotificationDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
