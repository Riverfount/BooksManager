package com.riverfount.booksmanager.catalog.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface CopyJpaRepository extends JpaRepository<CopyJpaEntity, Long> {

    boolean existsByAssetCode(String assetCode);
}
