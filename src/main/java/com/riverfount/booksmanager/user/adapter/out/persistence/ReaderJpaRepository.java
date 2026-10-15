package com.riverfount.booksmanager.user.adapter.out.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ReaderJpaRepository extends JpaRepository<ReaderJpaEntity, Long> {

    boolean existsByDocument(String document);

    boolean existsByDocumentAndIdNot(String document, Long id);

    /**
     * Listing (RF07). A null parameter means "no filter on that field". The
     * name comes already lowercased and wrapped as a LIKE pattern (see
     * {@link ReaderPersistenceAdapter}), using '!' as the escape character
     * so '%' and '_' typed by the user stay literal. The document is
     * normalized and matched by equality.
     */
    @Query("""
            select r from ReaderJpaEntity r
            where (:name is null or lower(r.name) like :name escape '!')
              and (:document is null or r.document = :document)
              and (:active is null or r.active = :active)
              and (:blocked is null or r.blocked = :blocked)
            """)
    Page<ReaderJpaEntity> search(@Param("name") String name, @Param("document") String document,
            @Param("active") Boolean active, @Param("blocked") Boolean blocked, Pageable pageable);
}
