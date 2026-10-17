package com.riverfount.booksmanager.user.application.service;

import com.riverfount.booksmanager.user.application.port.in.FindReaderNamesUseCase;
import com.riverfount.booksmanager.user.application.port.out.ReaderRepository;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What the user module offers the loan module's reports (RF23): the names of readers, in one query. A
 * class of its own, apart from {@link ReaderService}, because it serves another module; the user
 * module never depends on the loan. Only the name leaves the module, not the other personal data (RNF11).
 */
@Service
class ReaderNameLookupService implements FindReaderNamesUseCase {

    private final ReaderRepository readerRepository;

    ReaderNameLookupService(ReaderRepository readerRepository) {
        this.readerRepository = readerRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, String> findNames(Set<Long> readerIds) {
        return readerRepository.findNamesByIds(readerIds);
    }
}
