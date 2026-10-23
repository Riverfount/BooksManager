package com.riverfount.booksmanager.user.adapter.out.persistence;

import com.riverfount.booksmanager.shared.adapter.out.persistence.ConstraintViolations;
import com.riverfount.booksmanager.shared.adapter.out.persistence.SpringDataPages;
import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import com.riverfount.booksmanager.shared.domain.PageResult;
import com.riverfount.booksmanager.user.application.port.out.ReaderFilter;
import com.riverfount.booksmanager.user.application.port.out.ReaderRepository;
import com.riverfount.booksmanager.user.application.port.in.ReaderContact;
import com.riverfount.booksmanager.user.domain.Guardian;
import com.riverfount.booksmanager.user.domain.Reader;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
class ReaderPersistenceAdapter implements ReaderRepository {

    private final ReaderJpaRepository jpaRepository;

    ReaderPersistenceAdapter(ReaderJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Reader save(Reader reader) {
        var guardian = reader.getGuardian();
        var entity = new ReaderJpaEntity(reader.getId(), reader.getName(), reader.getDocument(),
                reader.getEmail(), reader.getPhone(), reader.getBirthDate(),
                guardian == null ? null : guardian.name(),
                guardian == null ? null : guardian.email(),
                guardian == null ? null : guardian.phone(),
                reader.isActive(), reader.isBlocked());
        try {
            // flushed here so that a duplicate document, also on an edit, fails inside this try
            return toDomain(jpaRepository.saveAndFlush(entity));
        } catch (DataIntegrityViolationException e) {
            // the database's unique constraint is what actually enforces the
            // unique document (RF07), including the race with the future
            // service's existsByDocument(); here we only translate it, and
            // only when the violation really is the document's.
            if (ConstraintViolations.violates(e, "reader_document_key")) {
                throw new BusinessRuleException(
                        "já existe um leitor com o documento '" + reader.getDocument() + "'");
            }
            throw e;
        }
    }

    @Override
    public Optional<Reader> findById(Long id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Map<Long, String> findNamesByIds(Set<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return jpaRepository.findNamesByIdIn(ids).stream()
                .collect(Collectors.toMap(ReaderJpaRepository.ReaderName::getId, ReaderJpaRepository.ReaderName::getName));
    }

    @Override
    public Map<Long, ReaderContact> findContactsByIds(Set<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return jpaRepository.findContactsByIdIn(ids).stream().collect(Collectors.toMap(
                ReaderJpaRepository.ReaderContactRow::getId,
                row -> new ReaderContact(row.getName(), row.getEmail())));
    }

    @Override
    public boolean existsByDocument(String document) {
        return jpaRepository.existsByDocument(document);
    }

    @Override
    public boolean existsByDocumentAndIdNot(String document, Long id) {
        return jpaRepository.existsByDocumentAndIdNot(document, id);
    }

    @Override
    public PageResult<Reader> findAll(ReaderFilter filter, PageQuery page) {
        var result = jpaRepository.search(likePattern(filter.name()), filter.document(), filter.active(),
                filter.blocked(), SpringDataPages.toPageable(page));
        return SpringDataPages.toPageResult(result, this::toDomain);
    }

    /**
     * "Contains" pattern, lowercased to match the lower() in the query, with
     * the LIKE wildcards escaped ('!' is the escape character); null stays null.
     */
    private static String likePattern(String term) {
        if (term == null) {
            return null;
        }
        var escaped = term.toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return "%" + escaped + "%";
    }

    private Reader toDomain(ReaderJpaEntity entity) {
        var guardian = entity.getGuardianName() == null ? null
                : new Guardian(entity.getGuardianName(), entity.getGuardianEmail(), entity.getGuardianPhone());
        return Reader.reconstitute(entity.getId(), entity.getName(), entity.getDocument(), entity.getEmail(),
                entity.getPhone(), entity.getBirthDate(), guardian, entity.isActive(), entity.isBlocked());
    }
}
