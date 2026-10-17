package com.riverfount.booksmanager.loan.application.port.out;

import com.riverfount.booksmanager.loan.domain.FineStatus;

/** Optional filters of the fine listing; a null field means "no filter on that field". */
public record FineFilter(Long readerId, Long loanId, FineStatus status) {

    public static FineFilter none() {
        return new FineFilter(null, null, null);
    }
}
