package com.riverfount.booksmanager.catalog.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface AuthorJpaRepository extends JpaRepository<AuthorJpaEntity, Long> {
}
