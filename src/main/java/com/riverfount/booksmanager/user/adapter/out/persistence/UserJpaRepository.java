package com.riverfount.booksmanager.user.adapter.out.persistence;

import com.riverfount.booksmanager.user.domain.Role;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface UserJpaRepository extends JpaRepository<UserJpaEntity, Long> {

    Optional<UserJpaEntity> findByLogin(String login);

    /** Same as {@link #findByLogin}, with a write lock on the row until the end of the transaction (SELECT ... FOR UPDATE). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserJpaEntity u where u.login = :login")
    Optional<UserJpaEntity> findByLoginForUpdate(@Param("login") String login);

    /** Finds by id with a write lock on the row until the end of the transaction (SELECT ... FOR UPDATE). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserJpaEntity u where u.id = :id")
    Optional<UserJpaEntity> findByIdForUpdate(@Param("id") Long id);

    /**
     * The active users with the profile, by id, each row locked until the end of the
     * transaction. The fixed order is what keeps two transactions that lock the same
     * rows from waiting for each other.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserJpaEntity u where u.active = true and u.role = :role order by u.id")
    List<UserJpaEntity> findActiveByRoleForUpdate(@Param("role") Role role);

    /** The active access of a reader, with a write lock on the row until the end of the transaction. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserJpaEntity u where u.readerId = :readerId and u.active = true")
    Optional<UserJpaEntity> findActiveByReaderIdForUpdate(@Param("readerId") Long readerId);

    boolean existsByLogin(String login);

    /**
     * Listing (RF09). A null parameter means "no filter on that field". The
     * login comes already normalized (lowercase, like the stored one) and
     * wrapped as a LIKE pattern (see {@link UserPersistenceAdapter}), using
     * '!' as the escape character so '%' and '_' typed by the user stay
     * literal. Without a role every profile is listed, the accesses of the readers included; with one, only that role.
     */
    @Query("""
            select u from UserJpaEntity u
            where (:login is null or u.login like :login escape '!')
              and (:active is null or u.active = :active)
              and (:role is null or u.role = :role)
            """)
    Page<UserJpaEntity> search(@Param("login") String login, @Param("active") Boolean active,
            @Param("role") Role role, Pageable pageable);
}
