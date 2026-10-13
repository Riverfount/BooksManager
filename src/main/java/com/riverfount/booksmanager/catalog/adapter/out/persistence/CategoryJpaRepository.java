package com.riverfount.booksmanager.catalog.adapter.out.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface CategoryJpaRepository extends JpaRepository<CategoryJpaEntity, Long> {

    boolean existsByName(String name);

    Page<CategoryJpaEntity> findByActive(boolean active, Pageable pageable);

    // Spring Data escapes LIKE wildcards (% and _) in the "containing" value
    Page<CategoryJpaEntity> findByActiveAndNameContainingIgnoreCase(boolean active, String name,
            Pageable pageable);
}
