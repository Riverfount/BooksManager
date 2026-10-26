package com.riverfount.booksmanager.loan.application.service;

import com.riverfount.booksmanager.loan.application.port.in.GetLoanUseCase;
import com.riverfount.booksmanager.loan.application.port.in.GetOwnLoanQuery;
import com.riverfount.booksmanager.loan.application.port.in.GetOwnLoanUseCase;
import com.riverfount.booksmanager.loan.application.port.in.ListLoansQuery;
import com.riverfount.booksmanager.loan.application.port.in.ListLoansUseCase;
import com.riverfount.booksmanager.loan.application.port.in.ListOwnLoansQuery;
import com.riverfount.booksmanager.loan.application.port.in.ListOwnLoansUseCase;
import com.riverfount.booksmanager.loan.application.port.in.LoanDetails;
import com.riverfount.booksmanager.loan.application.port.in.RenewLoanCommand;
import com.riverfount.booksmanager.loan.application.port.in.RenewLoanUseCase;
import com.riverfount.booksmanager.loan.application.port.in.RenewOwnLoanCommand;
import com.riverfount.booksmanager.loan.application.port.in.RenewOwnLoanUseCase;
import com.riverfount.booksmanager.shared.domain.Ownership;
import com.riverfount.booksmanager.shared.domain.PageResult;
import com.riverfount.booksmanager.shared.domain.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What a reader does with their own loans (Phase 6, RF13, RF14): the same use cases as at the desk, with the reader
 * fixed to the one of the token and the rule of the owner applied here, not in the controller. The rules of renewing
 * are the ones of {@link RenewLoanUseCase} (RN07, RN09); this only decides whose loan it is.
 *
 * <p>A loan of another reader is answered like one that does not exist ({@link Ownership}), and that is checked before
 * the renewal runs, so a refusal such as "already returned" never tells the state of a loan that is not the reader's.
 */
@Service
@Transactional
class OwnLoanService implements ListOwnLoansUseCase, GetOwnLoanUseCase, RenewOwnLoanUseCase {

    private final GetLoanUseCase getLoan;
    private final ListLoansUseCase listLoans;
    private final RenewLoanUseCase renewLoan;

    OwnLoanService(GetLoanUseCase getLoan, ListLoansUseCase listLoans, RenewLoanUseCase renewLoan) {
        this.getLoan = getLoan;
        this.listLoans = listLoans;
        this.renewLoan = renewLoan;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<LoanDetails> list(ListOwnLoansQuery query) {
        return listLoans.list(new ListLoansQuery(query.readerId(), null, query.status(), query.overdue(),
                query.page()));
    }

    @Override
    @Transactional(readOnly = true)
    public LoanDetails get(GetOwnLoanQuery query) {
        return ownDetails(query.loanId(), query.readerId());
    }

    @Override
    public LoanDetails renew(RenewOwnLoanCommand command) {
        ownDetails(command.loanId(), command.readerId());
        renewLoan.renew(new RenewLoanCommand(command.loanId()));
        return getLoan.findById(command.loanId())
                .orElseThrow(() -> new IllegalStateException("loan vanished inside its own transaction"));
    }

    /** The details of the loan, when it is the reader's; otherwise "not found", whatever the reason. */
    private LoanDetails ownDetails(Long loanId, Long readerId) {
        var notFound = "empréstimo não encontrado: " + loanId;
        var details = getLoan.findById(loanId).orElseThrow(() -> new ResourceNotFoundException(notFound));
        Ownership.requireOwner(details.loan().getReaderId(), readerId, notFound);
        return details;
    }
}
