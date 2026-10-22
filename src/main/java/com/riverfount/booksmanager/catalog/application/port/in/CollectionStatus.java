package com.riverfount.booksmanager.catalog.application.port.in;

import com.riverfount.booksmanager.catalog.domain.CopyStatus;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The status of the collection (RF25): the active copies in all and by status, and the inactive ones apart
 * (RN14). Every status is present in {@code activeByStatus}, in the order of the enum, with zero when no copy
 * has it, so the counts always add up to {@code totalActive}.
 *
 * @param totalActive the active copies
 * @param activeByStatus the active copies of each status
 * @param inactive the inactive copies, whatever their status
 */
public record CollectionStatus(long totalActive, Map<CopyStatus, Long> activeByStatus, long inactive) {

    public CollectionStatus {
        activeByStatus = Collections.unmodifiableMap(new LinkedHashMap<>(activeByStatus));
    }
}
