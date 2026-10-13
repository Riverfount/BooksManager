package com.riverfount.booksmanager.catalog.adapter.out.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface BookJpaRepository extends JpaRepository<BookJpaEntity, Long> {

    boolean existsByIsbn(String isbn);

    Page<BookJpaEntity> findByActive(boolean active, Pageable pageable);
}
