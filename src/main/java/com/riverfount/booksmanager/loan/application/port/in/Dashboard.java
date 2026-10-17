package com.riverfount.booksmanager.loan.application.port.in;

import com.riverfount.booksmanager.catalog.application.port.in.CategoryCopies;
import java.util.List;

/**
 * The numbers of the dashboard (RF23, RF25): the active copies, the active loans, the overdue loans, the
 * readers suspended today and what each category holds. Each one is the total of the matching listing,
 * except the suspended readers, which are counted by reader and not by suspension.
 */
public record Dashboard(long totalCopies, long activeLoans, long overdueLoans, long suspendedReaders,
        List<CategoryCopies> copiesByCategory) {
}
