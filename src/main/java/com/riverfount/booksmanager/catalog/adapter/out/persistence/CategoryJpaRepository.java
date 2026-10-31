package com.riverfount.booksmanager.catalog.adapter.out.persistence;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface CategoryJpaRepository extends JpaRepository<CategoryJpaEntity, Long> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    Page<CategoryJpaEntity> findByActive(boolean active, Pageable pageable);

    // Spring Data escapes LIKE wildcards (% and _) in the "containing" value
    Page<CategoryJpaEntity> findByActiveAndNameContainingIgnoreCase(boolean active, String name,
            Pageable pageable);

    /**
     * Every category with its active books and the active copies of its books, in one query with two
     * scalar subqueries (no join, so a book with many copies is not counted many times).
     */
    @Query("""
            select c.id as id, c.name as name, c.active as active,
                   (select count(b) from BookJpaEntity b where b.categoryId = c.id and b.active = true) as books,
                   (select count(cp) from CopyJpaEntity cp, BookJpaEntity bk
                     where cp.bookId = bk.id and bk.categoryId = c.id and cp.active = true) as copies
            from CategoryJpaEntity c
            order by lower(c.name), c.id
            """)
    List<CategoryCount> countBooksAndCopies();

    interface CategoryCount {

        Long getId();

        String getName();

        Boolean getActive();

        Long getBooks();

        Long getCopies();
    }
}
