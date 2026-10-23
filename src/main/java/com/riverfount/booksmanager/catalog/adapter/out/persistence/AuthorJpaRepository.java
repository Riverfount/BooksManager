package com.riverfount.booksmanager.catalog.adapter.out.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface AuthorJpaRepository extends JpaRepository<AuthorJpaEntity, Long> {

    @org.springframework.data.jpa.repository.Query(
            "select a from AuthorJpaEntity a where a.active = true and lower(a.name) in :lowerNames")
    java.util.List<AuthorJpaEntity> findActiveByLowerNames(
            @org.springframework.data.repository.query.Param("lowerNames") java.util.Collection<String> lowerNames);

    Page<AuthorJpaEntity> findByActive(boolean active, Pageable pageable);

    // Spring Data escapes LIKE wildcards (% and _) in the "containing" value
    Page<AuthorJpaEntity> findByActiveAndNameContainingIgnoreCase(boolean active, String name, Pageable pageable);
}
