package com.riverfount.booksmanager.catalogo.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface ExemplarJpaRepository extends JpaRepository<ExemplarJpaEntity, Long> {

    boolean existsByCodigoPatrimonio(String codigoPatrimonio);
}
