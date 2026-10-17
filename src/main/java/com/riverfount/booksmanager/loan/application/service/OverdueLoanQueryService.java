package com.riverfount.booksmanager.loan.application.service;

import com.riverfount.booksmanager.catalog.application.port.in.FindCopySummariesUseCase;
import com.riverfount.booksmanager.loan.application.port.in.ListOverdueLoansQuery;
import com.riverfount.booksmanager.loan.application.port.in.ListOverdueLoansUseCase;
import com.riverfount.booksmanager.loan.application.port.in.OverdueLoan;
import com.riverfount.booksmanager.loan.application.port.out.LoanFilter;
import com.riverfount.booksmanager.loan.application.port.out.LoanRepository;
import com.riverfount.booksmanager.loan.domain.Loan;
import com.riverfount.booksmanager.shared.domain.PageResult;
import com.riverfount.booksmanager.user.application.port.in.FindReaderNamesUseCase;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The overdue loans report (RF23, HU12, RNF14). It only reads: the late loans come ordered by due date
 * (the earlier, the more late) from the repository, the days late are the domain's
 * ({@link Loan#daysLate}) for the clock's day, and the readers and the copies of the whole page are
 * fetched through the public ports of their modules, one call each, not one per loan.
 */
@Service
@Transactional(readOnly = true)
class OverdueLoanQueryService implements ListOverdueLoansUseCase {

    private final LoanRepository loanRepository;
    private final FindReaderNamesUseCase readerNames;
    private final FindCopySummariesUseCase copySummaries;
    private final Clock clock;

    OverdueLoanQueryService(LoanRepository loanRepository, FindReaderNamesUseCase readerNames,
            FindCopySummariesUseCase copySummaries, Clock clock) {
        this.loanRepository = loanRepository;
        this.readerNames = readerNames;
        this.copySummaries = copySummaries;
        this.clock = clock;
    }

    @Override
    public PageResult<OverdueLoan> list(ListOverdueLoansQuery query) {
        var today = LocalDate.now(clock);
        var page = loanRepository.findAll(new LoanFilter(null, null, null, today), query.page());

        Set<Long> readerIds = page.items().stream().map(Loan::getReaderId).collect(Collectors.toSet());
        Set<Long> copyIds = page.items().stream().map(Loan::getCopyId).collect(Collectors.toSet());
        var names = readerNames.findNames(readerIds);
        var copies = copySummaries.findSummaries(copyIds);

        return page.map(loan -> new OverdueLoan(loan, loan.daysLate(today), names.get(loan.getReaderId()),
                copies.get(loan.getCopyId())));
    }
}
