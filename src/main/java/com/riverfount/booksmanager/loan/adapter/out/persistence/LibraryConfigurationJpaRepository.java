package com.riverfount.booksmanager.loan.adapter.out.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface LibraryConfigurationJpaRepository extends JpaRepository<LibraryConfigurationJpaEntity, Short> {

    /** Finds by id with a write lock on the row until the end of the transaction (SELECT ... FOR UPDATE). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from LibraryConfigurationJpaEntity c where c.id = :id")
    Optional<LibraryConfigurationJpaEntity> findByIdForUpdate(@Param("id") Short id);
}
