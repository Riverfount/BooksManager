package com.riverfount.booksmanager.loan.application.service;

import com.riverfount.booksmanager.catalog.application.port.in.CopySummary;
import com.riverfount.booksmanager.catalog.application.port.in.FindCopySummariesUseCase;
import com.riverfount.booksmanager.loan.application.port.in.ListMostBorrowedBooksQuery;
import com.riverfount.booksmanager.loan.application.port.in.ListMostBorrowedBooksUseCase;
import com.riverfount.booksmanager.loan.application.port.in.MostBorrowedBook;
import com.riverfount.booksmanager.loan.application.port.out.CopyLoanCount;
import com.riverfount.booksmanager.loan.application.port.out.LoanRepository;
import com.riverfount.booksmanager.shared.domain.PageResult;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The most borrowed books report (RF24, RNF14). The loan only knows the copy, and the book belongs to the
 * catalog, so the report is built in two steps that never join tables of another module: the loans are counted
 * per copy in one aggregated query of this module, and the copies are taken to their books through the public
 * port of the catalog (one call for all of them), where the counts are summed per book. Whatever the number of
 * loans, copies or books, it runs the same fixed number of queries. The summing, the ordering (most borrowed
 * first, ties broken by title and then by id, so the order is stable) and the page are done here, over the books
 * that had at least one loan in the period, which is a small set for a library of this size. It only reads.
 */
@Service
@Transactional(readOnly = true)
class MostBorrowedBooksQueryService implements ListMostBorrowedBooksUseCase {

    private static final Comparator<MostBorrowedBook> ORDER = Comparator
            .comparingLong(MostBorrowedBook::totalLoans).reversed()
            .thenComparing(MostBorrowedBook::title, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
            .thenComparing(MostBorrowedBook::bookId);

    private final LoanRepository loanRepository;
    private final FindCopySummariesUseCase copySummaries;

    MostBorrowedBooksQueryService(LoanRepository loanRepository, FindCopySummariesUseCase copySummaries) {
        this.loanRepository = loanRepository;
        this.copySummaries = copySummaries;
    }

    @Override
    public PageResult<MostBorrowedBook> list(ListMostBorrowedBooksQuery query) {
        var perCopy = loanRepository.countLoansByCopy(query.from(), query.to());
        var copies = copySummaries.findSummaries(perCopy.stream().map(CopyLoanCount::copyId)
                .collect(Collectors.toSet()));

        var totals = new HashMap<Long, Long>();
        var titles = new HashMap<Long, String>();
        for (var count : perCopy) {
            CopySummary copy = copies.get(count.copyId());
            if (copy == null) {
                continue; // a copy the catalog no longer knows cannot be placed in a book
            }
            totals.merge(copy.bookId(), count.total(), Long::sum);
            titles.putIfAbsent(copy.bookId(), copy.bookTitle());
        }

        List<MostBorrowedBook> ordered = totals.entrySet().stream()
                .map(entry -> new MostBorrowedBook(entry.getKey(), titles.get(entry.getKey()), entry.getValue()))
                .sorted(ORDER).toList();
        return pageOf(ordered, query);
    }

    private static PageResult<MostBorrowedBook> pageOf(List<MostBorrowedBook> ordered, ListMostBorrowedBooksQuery query) {
        var page = query.page();
        var fromIndex = (int) Math.min((long) page.page() * page.size(), ordered.size());
        var toIndex = Math.min(fromIndex + page.size(), ordered.size());
        return new PageResult<>(ordered.subList(fromIndex, toIndex), page.page(), page.size(), ordered.size());
    }
}
