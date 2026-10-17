package com.riverfount.booksmanager.catalog.application.port.in;

import java.util.Map;
import java.util.Set;

/** Public port for the loan module: the summaries of many copies in one call, for a report (RF23). */
public interface FindCopySummariesUseCase {

    /**
     * The summary of each given copy, active or not (the history keeps inactive copies); a copy that does
     * not exist is absent from the map.
     */
    Map<Long, CopySummary> findSummaries(Set<Long> copyIds);
}
