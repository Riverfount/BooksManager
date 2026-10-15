package com.riverfount.booksmanager.security.adapter.out.persistence;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface RefreshTokenJpaRepository extends JpaRepository<RefreshTokenJpaEntity, Long> {

    Optional<RefreshTokenJpaEntity> findByTokenHash(String tokenHash);

    /** Same as {@link #findByTokenHash}, with a write lock on the row until the end of the transaction (SELECT ... FOR UPDATE). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from RefreshTokenJpaEntity t where t.tokenHash = :tokenHash")
    Optional<RefreshTokenJpaEntity> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    /**
     * Revokes the tokens of the user that are not revoked yet, in one statement.
     * The session is flushed before and cleared after, so entities already loaded
     * do not hide the change.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RefreshTokenJpaEntity t set t.revokedAt = :now where t.userId = :userId and t.revokedAt is null")
    int revokeAllActive(@Param("userId") Long userId, @Param("now") Instant now);
}
