package com.riverfount.booksmanager.user.application.service;

import com.riverfount.booksmanager.user.application.port.in.FindReaderContactsUseCase;
import com.riverfount.booksmanager.user.application.port.in.ReaderContact;
import com.riverfount.booksmanager.user.application.port.out.ReaderRepository;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What the user module offers the loan module to notify readers (RF28): the name and the e-mail, in one query. A
 * class of its own, because it serves another module; the user module never depends on the loan. Nothing else of the
 * personal data leaves the module (RNF11).
 */
@Service
class ReaderContactLookupService implements FindReaderContactsUseCase {

    private final ReaderRepository readerRepository;

    ReaderContactLookupService(ReaderRepository readerRepository) {
        this.readerRepository = readerRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, ReaderContact> findContacts(Set<Long> readerIds) {
        return readerRepository.findContactsByIds(readerIds);
    }
}
