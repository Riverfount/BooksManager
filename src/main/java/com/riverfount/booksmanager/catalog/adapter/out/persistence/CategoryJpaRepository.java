package com.riverfount.booksmanager.catalog.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface CategoryJpaRepository extends JpaRepository<CategoryJpaEntity, Long> {

    boolean existsByName(String name);
}
