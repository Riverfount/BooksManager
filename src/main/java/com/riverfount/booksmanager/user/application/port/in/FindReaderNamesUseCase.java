package com.riverfount.booksmanager.user.application.port.in;

import java.util.Map;
import java.util.Set;

/** Public port for the loan module: the names of many readers in one call, for a report (RF23). Only the name. */
public interface FindReaderNamesUseCase {

    /**
     * The name of each given reader, active or not (the history keeps inactive readers); a reader that does
     * not exist is absent from the map.
     */
    Map<Long, String> findNames(Set<Long> readerIds);
}
