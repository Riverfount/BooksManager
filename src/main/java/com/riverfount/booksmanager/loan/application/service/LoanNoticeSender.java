package com.riverfount.booksmanager.loan.application.service;

import com.riverfount.booksmanager.catalog.application.port.in.CopySummary;
import com.riverfount.booksmanager.loan.application.port.out.LoanNoticeRepository;
import com.riverfount.booksmanager.loan.application.port.out.LoanRepository;
import com.riverfount.booksmanager.loan.domain.LoanNotice;
import com.riverfount.booksmanager.shared.application.port.out.NotificationSender;
import com.riverfount.booksmanager.user.application.port.in.ReaderContact;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sends one notice about one loan in a transaction of its own (RF28, RNF19). It is a bean apart from
 * {@link SendLoanNoticesService} so that each call goes through the transaction proxy: a failure here rolls back this
 * loan only. It reloads the loan and asks the domain again which notice is owed, so a loan returned or renewed since
 * the routine read it is left alone. The notice is recorded first (and flushed), then the message is sent and
 * confirmed: if the sending fails the exception rolls the record back, so the notice stays owed for the next run, and
 * if two runs overlap the second fails on the unique key before sending anything.
 */
@Service
@Transactional
class LoanNoticeSender {

    private final LoanRepository loanRepository;
    private final LoanNoticeRepository noticeRepository;
    private final NotificationSender notificationSender;

    LoanNoticeSender(LoanRepository loanRepository, LoanNoticeRepository noticeRepository,
            NotificationSender notificationSender) {
        this.loanRepository = loanRepository;
        this.noticeRepository = noticeRepository;
        this.notificationSender = notificationSender;
    }

    /**
     * @return whether the notice was sent; false when the loan is gone, was returned, or no longer owes exactly this
     *         notice today
     * @throws com.riverfount.booksmanager.shared.application.port.out.NotificationDeliveryException if the message
     *         could not be delivered; nothing is recorded then
     */
    boolean send(Long loanId, LoanNotice expected, int dueSoonNoticeDays, LocalDate today, ReaderContact reader,
            CopySummary copy) {
        var loan = loanRepository.findById(loanId).orElse(null);
        if (loan == null || !loan.noticeDue(today, dueSoonNoticeDays).filter(expected::equals).isPresent()) {
            return false;
        }
        noticeRepository.save(loanId, expected, today);
        notificationSender.sendAndConfirm(LoanNoticeMessages.of(reader, copy, loan, expected, today));
        return true;
    }
}
