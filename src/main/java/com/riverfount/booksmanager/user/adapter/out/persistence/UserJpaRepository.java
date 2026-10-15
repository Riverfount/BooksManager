package com.riverfount.booksmanager.user.adapter.out.persistence;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface UserJpaRepository extends JpaRepository<UserJpaEntity, Long> {

    Optional<UserJpaEntity> findByLogin(String login);

    boolean existsByLogin(String login);

    /**
     * Listing (RF09). A null parameter means "no filter on that field". The
     * login comes already normalized (lowercase, like the stored one) and
     * wrapped as a LIKE pattern (see {@link UserPersistenceAdapter}), using
     * '!' as the escape character so '%' and '_' typed by the user stay
     * literal.
     */
    @Query("""
            select u from UserJpaEntity u
            where (:login is null or u.login like :login escape '!')
              and (:active is null or u.active = :active)
            """)
    Page<UserJpaEntity> search(@Param("login") String login, @Param("active") Boolean active, Pageable pageable);
}
