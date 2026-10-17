package com.riverfount.booksmanager.catalog.adapter.out.persistence;

import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface BookJpaRepository extends JpaRepository<BookJpaEntity, Long> {

    boolean existsByIsbn(String isbn);

    /**
     * Listing and search (RF05). A null parameter means "no filter on that
     * field". The text parameters come already lowercased and wrapped as a
     * LIKE pattern (see {@link BookPersistenceAdapter}), using '!' as the
     * escape character so '%' and '_' typed by the user stay literal. The
     * author is matched with EXISTS, so a book with several matching
     * authors appears (and is counted) once.
     */
    @Query("""
            select b from BookJpaEntity b
            where b.active = :active
              and (:title is null or lower(b.title) like :title escape '!')
              and (:isbn is null or b.isbn like :isbn escape '!')
              and (:categoryId is null or b.categoryId = :categoryId)
              and (:author is null or exists (
                    select a.id from AuthorJpaEntity a
                    where a.id member of b.authorIds and lower(a.name) like :author escape '!'))
            """)
    Page<BookJpaEntity> search(@Param("title") String title, @Param("author") String author,
            @Param("isbn") String isbn, @Param("categoryId") Long categoryId,
            @Param("active") boolean active, Pageable pageable);

    /** The id and the title of each book, without loading the books (nor their authors). */
    @Query("select b.id as id, b.title as title from BookJpaEntity b where b.id in :ids")
    List<BookTitle> findTitlesByIdIn(@Param("ids") Collection<Long> ids);

    interface BookTitle {

        Long getId();

        String getTitle();
    }
}
