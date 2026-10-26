package com.riverfount.booksmanager.user.adapter.out.persistence;

import com.riverfount.booksmanager.user.domain.ReaderAccessToken.Purpose;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ReaderAccessTokenJpaRepository extends JpaRepository<ReaderAccessTokenJpaEntity, Long> {

    /** Finds by hash with a write lock on the row until the end of the transaction (SELECT ... FOR UPDATE). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from ReaderAccessTokenJpaEntity t where t.tokenHash = :tokenHash")
    Optional<ReaderAccessTokenJpaEntity> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update ReaderAccessTokenJpaEntity t set t.usedAt = :now
            where t.readerId = :readerId and t.purpose = :purpose and t.usedAt is null
            """)
    int invalidatePending(@Param("readerId") Long readerId, @Param("purpose") Purpose purpose,
            @Param("now") Instant now);
}
