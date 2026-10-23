package com.riverfount.booksmanager.user.application.port.out;

import com.riverfount.booksmanager.shared.domain.PageQuery;
import com.riverfount.booksmanager.shared.domain.PageResult;
import com.riverfount.booksmanager.user.application.port.in.ReaderContact;
import com.riverfount.booksmanager.user.domain.Reader;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Outbound port for Reader persistence, in the domain's language.
 */
public interface ReaderRepository {

    /**
     * Saves the reader. A document already used by another reader is
     * refused with a BusinessRuleException (RF07).
     */
    Reader save(Reader reader);

    Optional<Reader> findById(Long id);

    /**
     * The names of the readers with the given ids, in one query and without loading the readers; an
     * id that does not exist is absent from the map.
     */
    Map<Long, String> findNamesByIds(Set<Long> ids);

    /**
     * The name and the e-mail of the readers with the given ids, in one query and without loading the readers; an
     * id that does not exist is absent from the map.
     */
    Map<Long, ReaderContact> findContactsByIds(Set<Long> ids);

    /** The document must already be normalized (see Reader.normalizeDocument). */
    boolean existsByDocument(String document);

    /** Like {@link #existsByDocument}, ignoring the reader with the given id (for edits). */
    boolean existsByDocumentAndIdNot(String document, Long id);

    /**
     * Lists readers narrowed by the optional filters (RF07). The sort field
     * is one of the entity's sortable properties: id, name or document.
     */
    PageResult<Reader> findAll(ReaderFilter filter, PageQuery page);
}
