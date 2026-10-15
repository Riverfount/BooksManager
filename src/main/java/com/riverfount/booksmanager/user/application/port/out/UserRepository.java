package com.riverfount.booksmanager.user.application.port.out;

import com.riverfount.booksmanager.shared.domain.PageQuery;
import com.riverfount.booksmanager.shared.domain.PageResult;
import com.riverfount.booksmanager.user.domain.User;
import java.util.Optional;

/**
 * Outbound port for User persistence, in the domain's language. The logins
 * passed in are normalized by the implementation (see User.normalizeLogin),
 * so a lookup never depends on the caller remembering to do it.
 */
public interface UserRepository {

    /**
     * Saves the user. A login already used by another user is refused with a
     * BusinessRuleException (RF09).
     */
    User save(User user);

    Optional<User> findById(Long id);

    /** Finds by login, ignoring case; inactive users are found too, the caller decides (RF08). */
    Optional<User> findByLogin(String login);

    /**
     * Finds by login, ignoring case, for an authentication attempt: the row is
     * locked until the end of the current transaction, so simultaneous attempts
     * on the same user are counted one by one (otherwise parallel requests would
     * all read the same count and get around the limit of wrong passwords).
     * Inactive users are found too, the caller decides (RF08).
     */
    Optional<User> findByLoginForAuthentication(String login);

    boolean existsByLogin(String login);

    /** Total of users, active or not (the first administrator is only created when it is zero). */
    long count();

    /**
     * Lists users narrowed by the optional filters (RF09). The sort field is
     * one of the entity's sortable properties: id or login.
     */
    PageResult<User> findAll(UserFilter filter, PageQuery page);
}
