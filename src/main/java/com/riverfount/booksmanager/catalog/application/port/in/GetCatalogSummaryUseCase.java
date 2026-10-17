package com.riverfount.booksmanager.catalog.application.port.in;

/** Public port for the loan module: the catalog numbers of the dashboard, in one call (RF25). */
public interface GetCatalogSummaryUseCase {

    CatalogSummary get();
}
