package com.riverfount.booksmanager.catalogo.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface CategoriaJpaRepository extends JpaRepository<CategoriaJpaEntity, Long> {

    boolean existsByNome(String nome);
}
