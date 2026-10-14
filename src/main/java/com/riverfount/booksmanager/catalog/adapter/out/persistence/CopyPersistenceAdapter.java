package com.riverfount.booksmanager.catalog.adapter.out.persistence;

import com.riverfount.booksmanager.catalog.application.port.out.CopyRepository;
import com.riverfount.booksmanager.catalog.domain.Copy;
import com.riverfount.booksmanager.catalog.domain.CopyStatus;
import com.riverfount.booksmanager.shared.adapter.out.persistence.ConstraintViolations;
import com.riverfount.booksmanager.shared.adapter.out.persistence.SpringDataPages;
import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import com.riverfount.booksmanager.shared.domain.PageResult;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
class CopyPersistenceAdapter implements CopyRepository {

    private final CopyJpaRepository jpaRepository;

    CopyPersistenceAdapter(CopyJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Copy save(Copy copy) {
        var entity = new CopyJpaEntity(copy.getId(), copy.getBookId(),
                copy.getAssetCode(), copy.getStatus(), copy.isActive(), copy.getVersion());
        try {
            // saveAndFlush, not save: the optimistic lock (RNF17) needs to
            // fail here, synchronously, not only on a deferred flush (for
            // example, on the caller's transaction commit).
            var saved = jpaRepository.saveAndFlush(entity);
            return toDomain(saved);
        } catch (DataIntegrityViolationException e) {
            if (ConstraintViolations.violates(e, "copy_asset_code_key")) {
                throw new BusinessRuleException(
                        "já existe um exemplar com o código de patrimônio '" + copy.getAssetCode()
                                + "'");
            }
            throw e;
        }
    }

    @Override
    public Optional<Copy> findById(Long id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public boolean existsByAssetCode(String assetCode) {
        return jpaRepository.existsByAssetCode(assetCode);
    }

    @Override
    public PageResult<Copy> findAll(Long bookId, CopyStatus status, boolean active, PageQuery page) {
        var result = jpaRepository.search(bookId, status, active, SpringDataPages.toPageable(page));
        return SpringDataPages.toPageResult(result, this::toDomain);
    }

    private Copy toDomain(CopyJpaEntity entity) {
        return Copy.reconstitute(entity.getId(), entity.getBookId(), entity.getAssetCode(),
                entity.getStatus(), entity.isActive(), entity.getVersion());
    }
}
