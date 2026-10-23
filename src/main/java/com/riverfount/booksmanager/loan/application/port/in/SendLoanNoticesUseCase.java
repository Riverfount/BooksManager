package com.riverfount.booksmanager.loan.application.port.in;

/**
 * Tells the readers by e-mail that a loan is about to fall due and that a loan is late (RF28). It does not depend on
 * the scheduled routine, which is only a trigger for it, so it can be tested without a real clock. It is idempotent:
 * a notice that was already sent is recorded and never sent again, so running it twice in a row changes nothing the
 * second time.
 */
public interface SendLoanNoticesUseCase {

    /**
     * Sends, for every active loan, the notice it owes today that was not sent yet (the due-soon notice from the days
     * of notice of the library configuration up to the due date, then one overdue notice per band of seven late
     * days). Each loan works in a transaction of its own, so a failure (the mail server down, say) is logged, leaves
     * that notice unrecorded for the next run to try again, and does not hold the others back.
     *
     * @return how many notices were sent in this run
     */
    int sendNotices();
}
