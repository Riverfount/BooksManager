package com.riverfount.booksmanager.loan.application.service;

import com.riverfount.booksmanager.catalog.application.port.in.GetCatalogSummaryUseCase;
import com.riverfount.booksmanager.loan.application.port.in.Dashboard;
import com.riverfount.booksmanager.loan.application.port.in.GetDashboardUseCase;
import com.riverfount.booksmanager.loan.application.port.out.LoanFilter;
import com.riverfount.booksmanager.loan.application.port.out.LoanRepository;
import com.riverfount.booksmanager.loan.application.port.out.SuspensionRepository;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The dashboard (RF23, RF25). It only reads: the loans and the suspensions are counted with the same
 * filters as their listings, for the clock's day, so the numbers agree with the lists; the copies and the
 * categories come from the public port of the catalog. A fixed number of aggregated queries, whatever the
 * size of the library.
 */
@Service
@Transactional(readOnly = true)
class DashboardService implements GetDashboardUseCase {

    private final LoanRepository loanRepository;
    private final SuspensionRepository suspensionRepository;
    private final GetCatalogSummaryUseCase catalog;
    private final Clock clock;

    DashboardService(LoanRepository loanRepository, SuspensionRepository suspensionRepository,
            GetCatalogSummaryUseCase catalog, Clock clock) {
        this.loanRepository = loanRepository;
        this.suspensionRepository = suspensionRepository;
        this.catalog = catalog;
        this.clock = clock;
    }

    @Override
    public Dashboard get() {
        var today = LocalDate.now(clock);
        var summary = catalog.get();
        var activeLoans = loanRepository.count(new LoanFilter(null, null, true, null));
        var overdueLoans = loanRepository.count(new LoanFilter(null, null, null, today));
        var suspendedReaders = suspensionRepository.countReadersInForceOn(today);
        return new Dashboard(summary.totalCopies(), activeLoans, overdueLoans, suspendedReaders,
                summary.categories());
    }
}
