package com.riverfount.booksmanager.loan.application.service;

import com.riverfount.booksmanager.catalog.application.port.in.CheckOutCopyUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.FindCopyAvailabilityUseCase;
import com.riverfount.booksmanager.loan.application.port.in.RegisterLoanCommand;
import com.riverfount.booksmanager.loan.application.port.in.RegisterLoanUseCase;
import com.riverfount.booksmanager.loan.application.port.out.FineFilter;
import com.riverfount.booksmanager.loan.application.port.out.FineRepository;
import com.riverfount.booksmanager.loan.application.port.out.LibraryConfigurationRepository;
import com.riverfount.booksmanager.loan.application.port.out.LoanFilter;
import com.riverfount.booksmanager.loan.application.port.out.LoanRepository;
import com.riverfount.booksmanager.loan.application.port.out.SuspensionFilter;
import com.riverfount.booksmanager.loan.application.port.out.SuspensionRepository;
import com.riverfount.booksmanager.loan.domain.BorrowingEligibility;
import com.riverfount.booksmanager.loan.domain.BorrowingSituation;
import com.riverfount.booksmanager.loan.domain.FineStatus;
import com.riverfount.booksmanager.loan.domain.Loan;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import com.riverfount.booksmanager.shared.domain.ResourceNotFoundException;
import com.riverfount.booksmanager.user.application.port.in.GetReaderBorrowingStateUseCase;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registers the loan of a copy to a reader (RF11, HU07). It only orchestrates: gathers the
 * facts (the copy and the reader through the public ports of their modules, the loans, fines
 * and suspensions of the reader through this module's repositories, the date from the clock),
 * lets the domain decide ({@link BorrowingEligibility}, {@link Loan#create}) and persists. It
 * is one transaction (RNF16): if marking the copy or saving the loan fails, nothing stays; and
 * two requests for the same copy at the same time do not both pass (RNF17), because the copy
 * is versioned and the database allows only one active loan per copy.
 */
@Service
@Transactional
class RegisterLoanService implements RegisterLoanUseCase {

    private static final PageQuery JUST_THE_COUNT = PageQuery.of(0, 1, "id");

    private final LibraryConfigurationRepository configurationRepository;
    private final LoanRepository loanRepository;
    private final FineRepository fineRepository;
    private final SuspensionRepository suspensionRepository;
    private final FindCopyAvailabilityUseCase copyAvailability;
    private final CheckOutCopyUseCase checkOutCopy;
    private final GetReaderBorrowingStateUseCase readerState;
    private final Clock clock;

    RegisterLoanService(LibraryConfigurationRepository configurationRepository, LoanRepository loanRepository,
            FineRepository fineRepository, SuspensionRepository suspensionRepository,
            FindCopyAvailabilityUseCase copyAvailability, CheckOutCopyUseCase checkOutCopy,
            GetReaderBorrowingStateUseCase readerState, Clock clock) {
        this.configurationRepository = configurationRepository;
        this.loanRepository = loanRepository;
        this.fineRepository = fineRepository;
        this.suspensionRepository = suspensionRepository;
        this.copyAvailability = copyAvailability;
        this.checkOutCopy = checkOutCopy;
        this.readerState = readerState;
        this.clock = clock;
    }

    @Override
    public Loan register(RegisterLoanCommand command) {
        var today = LocalDate.now(clock);
        var configuration = configurationRepository.get();
        // built first: this way the ids are guaranteed non-null when the other modules are asked
        var loan = Loan.create(command.copyId(), command.readerId(), today, configuration.getLoanPeriodDays());

        BorrowingEligibility.check(situationOf(loan, today), configuration.getLoanLimit());

        checkOutCopy.checkOut(loan.getCopyId());
        return loanRepository.save(loan);
    }

    private BorrowingSituation situationOf(Loan loan, LocalDate today) {
        var copy = copyAvailability.findAvailability(loan.getCopyId())
                .orElseThrow(() -> new ResourceNotFoundException("exemplar não encontrado: " + loan.getCopyId()));
        var reader = readerState.getBorrowingState(loan.getReaderId());
        var readerId = loan.getReaderId();
        var activeLoans = loanRepository.findAll(new LoanFilter(readerId, null, true, null), JUST_THE_COUNT)
                .totalItems();
        var overdueLoans = loanRepository.findAll(new LoanFilter(readerId, null, null, today), JUST_THE_COUNT)
                .totalItems();
        var pendingFines = fineRepository.findAll(new FineFilter(readerId, null, FineStatus.PENDING), JUST_THE_COUNT)
                .totalItems();
        var suspensions = suspensionRepository.findAll(new SuspensionFilter(readerId, null, today), JUST_THE_COUNT)
                .totalItems();
        return new BorrowingSituation(copy.lendable(), reader.active(), reader.blocked(),
                Math.toIntExact(activeLoans), pendingFines > 0, suspensions > 0, overdueLoans > 0);
    }
}
