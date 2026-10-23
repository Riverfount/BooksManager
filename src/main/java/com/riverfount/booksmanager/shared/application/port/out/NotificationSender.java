package com.riverfount.booksmanager.shared.application.port.out;

/**
 * Outbound port that delivers a {@link Notification} (RF28). A use case calls it inside its transaction; the
 * delivery happens only after the commit and a failure never reaches the caller (RNF19), so an unavailable mail
 * server cannot undo or fail a loan, a return or a reservation.
 */
public interface NotificationSender {

    void send(Notification notification);

    /**
     * Delivers the {@link Notification} now, in the caller's thread and regardless of any transaction, and tells the
     * caller if it failed. For the routines that must know whether the message went out before they record it as
     * done (the loan notices, RF28); a use case that must not be affected by the mail uses {@link #send}.
     *
     * @throws NotificationDeliveryException if the message could not be delivered
     */
    void sendAndConfirm(Notification notification);
}
