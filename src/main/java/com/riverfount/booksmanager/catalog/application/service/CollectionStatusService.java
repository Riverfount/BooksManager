package com.riverfount.booksmanager.catalog.application.service;

import com.riverfount.booksmanager.catalog.application.port.in.CollectionStatus;
import com.riverfount.booksmanager.catalog.application.port.in.GetCollectionStatusUseCase;
import com.riverfount.booksmanager.catalog.application.port.out.CopyRepository;
import com.riverfount.booksmanager.catalog.domain.CopyStatus;
import java.util.EnumMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The status of the collection (RF25, RN14). It only reads, from one aggregated query of the copies grouped by
 * status and by active flag: the active ones are counted by status (a status with no copy is present with zero)
 * and the inactive ones apart, so a copy that was inactivated never counts as active. The total of active copies
 * is the sum of the counts by status, so the two always agree.
 */
@Service
@Transactional(readOnly = true)
class CollectionStatusService implements GetCollectionStatusUseCase {

    private final CopyRepository copyRepository;

    CollectionStatusService(CopyRepository copyRepository) {
        this.copyRepository = copyRepository;
    }

    @Override
    public CollectionStatus get() {
        var activeByStatus = new EnumMap<CopyStatus, Long>(CopyStatus.class);
        for (var status : CopyStatus.values()) {
            activeByStatus.put(status, 0L);
        }
        var inactive = 0L;
        for (var row : copyRepository.countByStatus()) {
            if (row.active()) {
                activeByStatus.merge(row.status(), row.total(), Long::sum);
            } else {
                inactive += row.total();
            }
        }
        var totalActive = activeByStatus.values().stream().mapToLong(Long::longValue).sum();
        return new CollectionStatus(totalActive, activeByStatus, inactive);
    }
}
