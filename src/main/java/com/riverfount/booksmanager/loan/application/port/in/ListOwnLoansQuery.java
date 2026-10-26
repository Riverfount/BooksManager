package com.riverfount.booksmanager.loan.application.port.in;

import com.riverfount.booksmanager.shared.domain.PageQuery;

/**
 * The loans of the reader (Phase 6, RF14, RNF14), by situation, with the same meaning of {@code overdue} as the
 * listing of the desk. The reader is part of the query, so there is no way to list the loans of somebody else; a null
 * situation means all of them, the returned ones (the history) included.
 */
public record ListOwnLoansQuery(Long readerId, LoanStatus status, boolean overdue, PageQuery page) {
}
