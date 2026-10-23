package com.riverfount.booksmanager.loan.application.service;

import com.riverfount.booksmanager.catalog.application.port.in.CopySummary;
import com.riverfount.booksmanager.catalog.application.port.in.FindCopySummariesUseCase;
import com.riverfount.booksmanager.loan.application.port.in.SendLoanNoticesUseCase;
import com.riverfount.booksmanager.loan.application.port.out.LibraryConfigurationRepository;
import com.riverfount.booksmanager.loan.application.port.out.LoanNoticeRepository;
import com.riverfount.booksmanager.loan.application.port.out.LoanRepository;
import com.riverfount.booksmanager.loan.application.port.out.SentLoanNotice;
import com.riverfount.booksmanager.loan.domain.Loan;
import com.riverfount.booksmanager.loan.domain.LoanNotice;
import com.riverfount.booksmanager.user.application.port.in.FindReaderContactsUseCase;
import com.riverfount.booksmanager.user.application.port.in.ReaderContact;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Tells the readers that a loan is about to fall due or is late (RF28). It first reads, with a fixed number of
 * queries: the active loans due up to today plus the days of notice (the library configuration, RN16) and every late
 * one, the notices already sent, and the contacts and book titles of the whole batch, not one per loan. It is not
 * transactional itself, like the expiration of reservations: each notice goes to the {@link LoanNoticeSender}, which
 * works in a transaction of its own, so a loan that fails (the mail server down, say) is logged and the others go on,
 * and its notice is tried again by the next run. Today is the clock's, and the domain decides what is owed.
 */
@Service
class SendLoanNoticesService implements SendLoanNoticesUseCase {

    private static final Logger log = LoggerFactory.getLogger(SendLoanNoticesService.class);

    private final LoanRepository loanRepository;
    private final LoanNoticeRepository noticeRepository;
    private final LibraryConfigurationRepository configurationRepository;
    private final FindReaderContactsUseCase readerContacts;
    private final FindCopySummariesUseCase copySummaries;
    private final LoanNoticeSender sender;
    private final Clock clock;

    SendLoanNoticesService(LoanRepository loanRepository, LoanNoticeRepository noticeRepository,
            LibraryConfigurationRepository configurationRepository, FindReaderContactsUseCase readerContacts,
            FindCopySummariesUseCase copySummaries, LoanNoticeSender sender, Clock clock) {
        this.loanRepository = loanRepository;
        this.noticeRepository = noticeRepository;
        this.configurationRepository = configurationRepository;
        this.readerContacts = readerContacts;
        this.copySummaries = copySummaries;
        this.sender = sender;
        this.clock = clock;
    }

    @Override
    public int sendNotices() {
        var today = LocalDate.now(clock);
        var batch = readBatch(today);

        var sent = 0;
        for (var entry : batch.owed().entrySet()) {
            var loan = entry.getKey();
            var reader = batch.readers().get(loan.getReaderId());
            if (reader == null) {
                log.warn("Could not notify the loan {}: its reader was not found.", loan.getId());
                continue;
            }
            try {
                if (sender.send(loan.getId(), entry.getValue(), batch.dueSoonNoticeDays(), today, reader,
                        batch.copies().get(loan.getCopyId()))) {
                    sent++;
                }
            } catch (RuntimeException e) {
                log.warn("Could not notify the loan {}; it will be tried again in the next run.", loan.getId(), e);
            }
        }
        return sent;
    }

    private Batch readBatch(LocalDate today) {
        var dueSoonNoticeDays = configurationRepository.get().getDueSoonNoticeDays();
        var candidates = loanRepository.findActiveDueOnOrBefore(today.plusDays(dueSoonNoticeDays));
        var alreadySent = noticeRepository
                .findSentByLoanIds(candidates.stream().map(Loan::getId).collect(Collectors.toSet()));

        Map<Loan, LoanNotice> owed = new LinkedHashMap<>();
        for (var loan : candidates) {
            loan.noticeDue(today, dueSoonNoticeDays)
                    .filter(notice -> !alreadySent.contains(new SentLoanNotice(loan.getId(), notice)))
                    .ifPresent(notice -> owed.put(loan, notice));
        }
        Set<Long> readerIds = owed.keySet().stream().map(Loan::getReaderId).collect(Collectors.toSet());
        Set<Long> copyIds = owed.keySet().stream().map(Loan::getCopyId).collect(Collectors.toSet());
        return new Batch(dueSoonNoticeDays, owed, readerContacts.findContacts(readerIds),
                copySummaries.findSummaries(copyIds));
    }

    private record Batch(int dueSoonNoticeDays, Map<Loan, LoanNotice> owed, Map<Long, ReaderContact> readers,
            Map<Long, CopySummary> copies) {
    }
}
