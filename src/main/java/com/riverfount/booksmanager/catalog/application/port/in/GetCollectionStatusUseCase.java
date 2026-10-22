package com.riverfount.booksmanager.catalog.application.port.in;

/** The total of copies and how many are in each status, in one call (RF25). */
public interface GetCollectionStatusUseCase {

    CollectionStatus get();
}
