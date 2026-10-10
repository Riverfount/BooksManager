package com.riverfount.booksmanager.catalogo.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface LivroJpaRepository extends JpaRepository<LivroJpaEntity, Long> {

    boolean existsByIsbn(String isbn);
}
