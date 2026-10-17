package com.riverfount.booksmanager.catalog.application.port.out;

import java.util.List;

/** Outbound port for the catalog counts of the dashboard (RF25), in the domain's language. */
public interface CatalogSummaryRepository {

    /**
     * Every category with its active books and the active copies of its books (the copies of an inactive
     * book count: they are still copies of the library), in one aggregated query, in order of name
     * without caring about case. A category with nothing in it comes with zeros.
     */
    List<CategoryCopyCount> countByCategory();
}
