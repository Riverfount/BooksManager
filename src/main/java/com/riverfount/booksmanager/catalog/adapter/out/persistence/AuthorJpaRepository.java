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

    /**
     * Listing by name (RF05), without accents (the unaccent extension, on both sides). The pattern comes already
     * lowercased and wrapped (see {@link LikePatterns}), with '!' as the escape character so '%' and '_' stay literal.
     */
    @org.springframework.data.jpa.repository.Query("""
            select a from AuthorJpaEntity a
            where a.active = :active
              and cast(function('unaccent', lower(a.name)) as string) like cast(function('unaccent', cast(:name as string)) as string) escape '!'
            """)
    Page<AuthorJpaEntity> searchByName(
            @org.springframework.data.repository.query.Param("name") String name,
            @org.springframework.data.repository.query.Param("active") boolean active, Pageable pageable);
}
