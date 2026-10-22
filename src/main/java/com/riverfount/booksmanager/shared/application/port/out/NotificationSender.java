package com.riverfount.booksmanager.shared.application.port.out;

/**
 * Outbound port that delivers a {@link Notification} (RF28). A use case calls it inside its transaction; the
 * delivery happens only after the commit and a failure never reaches the caller (RNF19), so an unavailable mail
 * server cannot undo or fail a loan, a return or a reservation.
 */
public interface NotificationSender {

    void send(Notification notification);
}
