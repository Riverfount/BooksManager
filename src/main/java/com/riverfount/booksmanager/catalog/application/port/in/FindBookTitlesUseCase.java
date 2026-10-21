package com.riverfount.booksmanager.catalog.application.port.in;

import java.util.Map;
import java.util.Set;

/** Public port for the loan module: the titles of many books in one call, for a listing (RF16). */
public interface FindBookTitlesUseCase {

    /** The title of each given book, active or not; a book that does not exist is absent from the map. */
    Map<Long, String> findTitles(Set<Long> bookIds);
}
