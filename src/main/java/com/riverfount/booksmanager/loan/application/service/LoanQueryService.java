package com.riverfount.booksmanager.loan.application.service;

import com.riverfount.booksmanager.loan.application.port.in.GetLoanUseCase;
import com.riverfount.booksmanager.loan.application.port.in.ListLoansQuery;
import com.riverfount.booksmanager.loan.application.port.in.ListLoansUseCase;
import com.riverfount.booksmanager.loan.application.port.in.LoanDetails;
import com.riverfount.booksmanager.loan.application.port.in.LoanStatus;
import com.riverfount.booksmanager.loan.application.port.out.FineRepository;
import com.riverfount.booksmanager.loan.application.port.out.LoanFilter;
import com.riverfount.booksmanager.loan.application.port.out.LoanRepository;
import com.riverfount.booksmanager.loan.application.port.out.SuspensionRepository;
import com.riverfount.booksmanager.loan.domain.Loan;
import com.riverfount.booksmanager.shared.domain.PageResult;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Queries on loans (RF14, HU30, RNF14): finds one or lists a page, active or closed, each with
 * the result of its penalty. The fines and suspensions of a whole page are fetched in one query
 * each, not one per loan. Nothing here decides anything: it only reads.
 */
@Service
@Transactional(readOnly = true)
class LoanQueryService implements GetLoanUseCase, ListLoansUseCase {

    private final LoanRepository loanRepository;
    private final FineRepository fineRepository;
    private final SuspensionRepository suspensionRepository;

    LoanQueryService(LoanRepository loanRepository, FineRepository fineRepository,
            SuspensionRepository suspensionRepository) {
        this.loanRepository = loanRepository;
        this.fineRepository = fineRepository;
        this.suspensionRepository = suspensionRepository;
    }

    @Override
    public Optional<LoanDetails> findById(Long id) {
        return loanRepository.findById(id).map(loan -> withPenalty(List.of(loan)).get(0));
    }

    @Override
    public PageResult<LoanDetails> list(ListLoansQuery query) {
        var page = loanRepository.findAll(filterOf(query), query.page());
        return new PageResult<>(withPenalty(page.items()), page.page(), page.size(), page.totalItems());
    }

    private static LoanFilter filterOf(ListLoansQuery query) {
        Boolean active = query.status() == null ? null : query.status() == LoanStatus.ACTIVE;
        return new LoanFilter(query.readerId(), query.copyId(), active, null);
    }

    private List<LoanDetails> withPenalty(List<Loan> loans) {
        Set<Long> loanIds = loans.stream().map(Loan::getId).collect(Collectors.toSet());
        var fines = fineRepository.findByLoanIds(loanIds);
        var suspensions = suspensionRepository.findByLoanIds(loanIds);
        return loans.stream()
                .map(loan -> new LoanDetails(loan, fines.get(loan.getId()), suspensions.get(loan.getId())))
                .toList();
    }
}
