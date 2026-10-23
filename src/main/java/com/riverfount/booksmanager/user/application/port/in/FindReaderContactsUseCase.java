package com.riverfount.booksmanager.user.application.port.in;

import java.util.Map;
import java.util.Set;

/** Public port for the loan module: the contact of many readers in one call, to notify them (RF28). */
public interface FindReaderContactsUseCase {

    /**
     * The contact of each given reader, active or not (a loan in progress outlives the registration status); a
     * reader that does not exist is absent from the map.
     */
    Map<Long, ReaderContact> findContacts(Set<Long> readerIds);
}
