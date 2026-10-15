package com.riverfount.booksmanager.user.adapter.out.persistence;

import com.riverfount.booksmanager.shared.adapter.out.persistence.ConstraintViolations;
import com.riverfount.booksmanager.shared.adapter.out.persistence.SpringDataPages;
import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import com.riverfount.booksmanager.shared.domain.PageResult;
import com.riverfount.booksmanager.user.application.port.out.UserFilter;
import com.riverfount.booksmanager.user.application.port.out.UserRepository;
import com.riverfount.booksmanager.user.domain.Role;
import com.riverfount.booksmanager.user.domain.User;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
class UserPersistenceAdapter implements UserRepository {

    private final UserJpaRepository jpaRepository;

    UserPersistenceAdapter(UserJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public User save(User user) {
        var entity = new UserJpaEntity(user.getId(), user.getLogin(), user.getPasswordHash(), user.getRole(),
                user.isActive(), user.getFailedLoginAttempts(), user.getBlockedUntil());
        try {
            return toDomain(jpaRepository.saveAndFlush(entity));
        } catch (DataIntegrityViolationException e) {
            // the database's unique constraint is what actually enforces the
            // unique login (RF09), including the race with a future service's
            // existsByLogin(); here we only translate it, and only when the
            // violation really is the login's.
            if (ConstraintViolations.violates(e, "app_user_login_key")) {
                throw new BusinessRuleException("já existe um usuário com o login '" + user.getLogin() + "'");
            }
            throw e;
        }
    }

    @Override
    public Optional<User> findById(Long id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<User> findByLogin(String login) {
        return jpaRepository.findByLogin(User.normalizeLogin(login)).map(this::toDomain);
    }

    @Override
    public Optional<User> findByLoginForAuthentication(String login) {
        return jpaRepository.findByLoginForUpdate(User.normalizeLogin(login)).map(this::toDomain);
    }

    @Override
    public Optional<User> findByIdForUpdate(Long id) {
        return jpaRepository.findByIdForUpdate(id).map(this::toDomain);
    }

    @Override
    public List<User> findActiveByRoleForUpdate(Role role) {
        return jpaRepository.findActiveByRoleForUpdate(role).stream().map(this::toDomain).toList();
    }

    @Override
    public boolean existsByLogin(String login) {
        return jpaRepository.existsByLogin(User.normalizeLogin(login));
    }

    @Override
    public long count() {
        return jpaRepository.count();
    }

    @Override
    public PageResult<User> findAll(UserFilter filter, PageQuery page) {
        var result = jpaRepository.search(likePattern(filter.login()), filter.active(),
                SpringDataPages.toPageable(page));
        return SpringDataPages.toPageResult(result, this::toDomain);
    }

    /** "Contains" pattern with the LIKE wildcards escaped ('!' is the escape character); null stays null. */
    private static String likePattern(String term) {
        if (term == null) {
            return null;
        }
        var escaped = term.replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return "%" + escaped + "%";
    }

    private User toDomain(UserJpaEntity entity) {
        return User.reconstitute(entity.getId(), entity.getLogin(), entity.getPasswordHash(), entity.getRole(),
                entity.isActive(), entity.getFailedLoginAttempts(), entity.getBlockedUntil());
    }
}
