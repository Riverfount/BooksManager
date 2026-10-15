package com.riverfount.booksmanager.user.application.service;

import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import com.riverfount.booksmanager.shared.domain.PageResult;
import com.riverfount.booksmanager.shared.domain.SortDirection;
import com.riverfount.booksmanager.user.application.port.out.UserFilter;
import com.riverfount.booksmanager.user.application.port.out.UserRepository;
import com.riverfount.booksmanager.user.domain.User;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory implementation of UserRepository, used only in the application
 * services' unit tests (no Spring, no database). Like the database, it
 * refuses a duplicate login, and like the JPA adapter it never hands out the
 * stored instance. Does not need to be thread-safe: it is a test fake, used
 * from a single thread.
 */
class InMemoryUserRepository implements UserRepository {

    private static final Map<String, Comparator<User>> COMPARATORS = Map.of(
            "id", Comparator.comparing(User::getId),
            "login", Comparator.comparing(User::getLogin));

    private final Map<Long, User> users = new HashMap<>();
    private long nextId = 1;
    private int saves;

    private static User copyWithId(User user, Long id) {
        return User.reconstitute(id, user.getLogin(), user.getPasswordHash(), user.getRole(), user.isActive(),
                user.getFailedLoginAttempts(), user.getBlockedUntil());
    }

    @Override
    public User save(User user) {
        saves++;
        var duplicate = users.values().stream()
                .anyMatch(other -> other.getLogin().equals(user.getLogin()) && !other.getId().equals(user.getId()));
        if (duplicate) {
            throw new BusinessRuleException("já existe um usuário com o login '" + user.getLogin() + "'");
        }
        var id = user.getId() != null ? user.getId() : nextId++;
        users.put(id, copyWithId(user, id));
        return copyWithId(user, id);
    }

    @Override
    public Optional<User> findById(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("o id não pode ser nulo");
        }
        return Optional.ofNullable(users.get(id)).map(user -> copyWithId(user, id));
    }

    @Override
    public Optional<User> findByLogin(String login) {
        var normalized = User.normalizeLogin(login);
        return users.values().stream().filter(user -> user.getLogin().equals(normalized)).findFirst()
                .map(user -> copyWithId(user, user.getId()));
    }

    @Override
    public Optional<User> findByLoginForAuthentication(String login) {
        return findByLogin(login);
    }

    int saves() {
        return saves;
    }

    @Override
    public boolean existsByLogin(String login) {
        return findByLogin(login).isPresent();
    }

    @Override
    public long count() {
        return users.size();
    }

    @Override
    public PageResult<User> findAll(UserFilter filter, PageQuery page) {
        var comparator = COMPARATORS.get(page.sortBy());
        if (comparator == null) {
            throw new IllegalArgumentException("campo de ordenação não suportado: " + page.sortBy());
        }
        if (page.direction() == SortDirection.DESC) {
            comparator = comparator.reversed();
        }
        List<User> sorted = users.values().stream()
                .filter(user -> filter.login() == null || user.getLogin().contains(filter.login()))
                .filter(user -> filter.active() == null || user.isActive() == filter.active())
                .sorted(comparator.thenComparing(User::getId))
                .toList();
        var items = sorted.stream().skip((long) page.page() * page.size()).limit(page.size())
                .map(user -> copyWithId(user, user.getId())).toList();
        return new PageResult<>(items, page.page(), page.size(), sorted.size());
    }
}
