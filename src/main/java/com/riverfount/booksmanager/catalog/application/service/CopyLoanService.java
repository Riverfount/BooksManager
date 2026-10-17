package com.riverfount.booksmanager.catalog.application.service;

import com.riverfount.booksmanager.catalog.application.port.in.CheckInCopyUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.CheckOutCopyUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.CopyAvailability;
import com.riverfount.booksmanager.catalog.application.port.in.FindCopyAvailabilityUseCase;
import com.riverfount.booksmanager.catalog.application.port.out.CopyRepository;
import com.riverfount.booksmanager.catalog.domain.Copy;
import com.riverfount.booksmanager.catalog.domain.CopyStatus;
import com.riverfount.booksmanager.shared.domain.ResourceNotFoundException;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What the catalog offers the loan module (RN03, RNF17): to see whether a copy can be
 * borrowed, to mark it borrowed and to mark it available again. It is a class of its own,
 * apart from {@link CopyService}, because it serves another module and the catalog never
 * depends on the loan, so the dependency points one way only. Each call is a transaction;
 * the {@code @Version} of the copy makes two simultaneous changes not both pass, and the
 * loan use case, in the same transaction, undoes everything if the other half fails.
 */
@Service
@Transactional
class CopyLoanService implements FindCopyAvailabilityUseCase, CheckOutCopyUseCase, CheckInCopyUseCase {

    private final CopyRepository copyRepository;

    CopyLoanService(CopyRepository copyRepository) {
        this.copyRepository = copyRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CopyAvailability> findAvailability(Long copyId) {
        return copyRepository.findById(copyId)
                .map(copy -> new CopyAvailability(copy.getId(), copy.isActive()
                        && copy.getStatus() == CopyStatus.AVAILABLE));
    }

    @Override
    public void checkOut(Long copyId) {
        var copy = load(copyId);
        copy.checkOut();
        copyRepository.save(copy);
    }

    @Override
    public void checkIn(Long copyId) {
        var copy = load(copyId);
        var before = copy.getStatus();
        copy.checkIn();
        if (copy.getStatus() != before) {
            copyRepository.save(copy);
        }
    }

    private Copy load(Long copyId) {
        return copyRepository.findById(copyId)
                .orElseThrow(() -> new ResourceNotFoundException("exemplar não encontrado: " + copyId));
    }
}
