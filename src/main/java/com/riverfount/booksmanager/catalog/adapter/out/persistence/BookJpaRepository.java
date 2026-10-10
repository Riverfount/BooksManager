package com.riverfount.booksmanager.catalog.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface BookJpaRepository extends JpaRepository<BookJpaEntity, Long> {

    boolean existsByIsbn(String isbn);
}
