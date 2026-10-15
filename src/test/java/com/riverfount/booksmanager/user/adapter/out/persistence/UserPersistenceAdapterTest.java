package com.riverfount.booksmanager.user.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.TestcontainersConfiguration;
import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import com.riverfount.booksmanager.user.application.port.out.UserFilter;
import com.riverfount.booksmanager.user.domain.Role;
import com.riverfount.booksmanager.user.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Tests the User persistence adapter against a real PostgreSQL
 * (Testcontainers): the mapping between the domain and the JPA entity, the
 * unique login (RF08, RF09) and the listing filters.
 */
@Import({TestcontainersConfiguration.class, UserPersistenceAdapter.class})
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class UserPersistenceAdapterTest {

    private static final String HASH = "$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5BzqhZ8H9V5xQe4Fq0sDq6Z0yR3Wm";

    @Autowired
    private UserPersistenceAdapter adapter;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User saveUser(String login, Role role) {
        return adapter.save(User.create(login, HASH, role));
    }

    private static PageQuery byLogin() {
        return PageQuery.of(0, 20, "login");
    }

    // mapping

    @Test
    void shouldSaveAndFindTheUserWithTheSameState() {
        var saved = saveUser("Maria", Role.ADMIN);

        var found = adapter.findById(saved.getId()).orElseThrow();

        assertThat(saved.getId()).isNotNull();
        assertThat(found.getLogin()).isEqualTo("maria");
        assertThat(found.getPasswordHash()).isEqualTo(HASH);
        assertThat(found.getRole()).isEqualTo(Role.ADMIN);
        assertThat(found.isActive()).isTrue();
    }

    @Test
    void shouldStoreTheRoleAsTextAndTheHashAsReceived() {
        saveUser("maria", Role.OPERATOR);

        var row = jdbcTemplate.queryForMap("select role, password_hash from app_user where login = 'maria'");

        assertThat(row.get("role")).isEqualTo("OPERATOR");
        assertThat(row.get("password_hash")).isEqualTo(HASH);
    }

    @Test
    void shouldPersistRoleHashAndDeactivationOfAnExistingUser() {
        var saved = saveUser("maria", Role.OPERATOR);
        saved.changeRole(Role.ADMIN);
        saved.changePasswordHash("$2a$10$newhashnewhashnewhashnewhashnewhashnewhashnewhashnewha");
        saved.deactivate();
        adapter.save(saved);

        var found = adapter.findById(saved.getId()).orElseThrow();

        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(found.getRole()).isEqualTo(Role.ADMIN);
        assertThat(found.getPasswordHash()).startsWith("$2a$10$newhash");
        assertThat(found.isActive()).isFalse();
        assertThat(adapter.count()).isEqualTo(1);
    }

    @Test
    void shouldReturnEmptyForNonExistentId() {
        assertThat(adapter.findById(999_999L)).isEmpty();
    }

    // unique login and lookup by login (RF08)

    @Test
    void duplicateLoginShouldViolateTheUniqueConstraintInTheDatabase() {
        saveUser("maria", Role.ADMIN);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "insert into app_user (login, password_hash, role) values ('maria', ?, 'OPERATOR')", HASH))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void duplicateLoginShouldBecomeBusinessRuleExceptionOnCreation() {
        saveUser("maria", Role.ADMIN);

        assertThatThrownBy(() -> saveUser("maria", Role.OPERATOR))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("maria");
    }

    @Test
    void aLoginThatDiffersOnlyByCaseShouldBeADuplicate() {
        saveUser("maria", Role.ADMIN);

        assertThatThrownBy(() -> saveUser("MARIA", Role.OPERATOR)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void shouldNotConfuseOtherIntegrityViolationsWithADuplicateLogin() {
        // a hash longer than the column is not a duplicate login
        var tooLong = User.reconstitute(null, "maria", "x".repeat(User.MAX_PASSWORD_HASH_LENGTH + 1),
                Role.OPERATOR, true);

        assertThatThrownBy(() -> adapter.save(tooLong)).isNotInstanceOf(BusinessRuleException.class);
    }

    @Test
    void findByLoginShouldIgnoreCaseAndSurroundingSpaces() {
        var saved = saveUser("maria", Role.OPERATOR);

        assertThat(adapter.findByLogin("maria")).hasValueSatisfying(user -> assertThat(user.getId()).isEqualTo(saved.getId()));
        assertThat(adapter.findByLogin("MARIA")).isPresent();
        assertThat(adapter.findByLogin("  Maria ")).isPresent();
    }

    @Test
    void findByLoginShouldReturnEmptyWhenThereIsNoSuchUserOrNoLogin() {
        saveUser("maria", Role.OPERATOR);

        assertThat(adapter.findByLogin("joao")).isEmpty();
        assertThat(adapter.findByLogin("mari")).isEmpty();
        assertThat(adapter.findByLogin(null)).isEmpty();
        assertThat(adapter.findByLogin("")).isEmpty();
    }

    @Test
    void existsByLoginShouldIgnoreCase() {
        saveUser("maria", Role.OPERATOR);

        assertThat(adapter.existsByLogin("maria")).isTrue();
        assertThat(adapter.existsByLogin("MARIA")).isTrue();
        assertThat(adapter.existsByLogin("joao")).isFalse();
    }

    @Test
    void shouldFindAnInactiveUserByLoginSoTheCallerCanRefuseIt() {
        var saved = saveUser("maria", Role.OPERATOR);
        saved.deactivate();
        adapter.save(saved);

        var found = adapter.findByLogin("maria").orElseThrow();

        assertThat(found.isActive()).isFalse();
        assertThat(found.canAuthenticate()).isFalse();
    }

    @Test
    void countShouldReflectTheStoredUsers() {
        assertThat(adapter.count()).isZero();

        saveUser("maria", Role.ADMIN);
        saveUser("joao", Role.OPERATOR);

        assertThat(adapter.count()).isEqualTo(2);
    }

    // listing and filters (RF09, RNF14)

    @Test
    void shouldListAllUsersWithoutFilters() {
        saveUser("bruno", Role.OPERATOR);
        saveUser("ana", Role.ADMIN);

        var page = adapter.findAll(UserFilter.none(), byLogin());

        assertThat(page.items()).extracting(User::getLogin).containsExactly("ana", "bruno");
        assertThat(page.totalItems()).isEqualTo(2);
    }

    @Test
    void shouldFilterByLoginContainingIgnoringCase() {
        saveUser("maria.silva", Role.OPERATOR);
        saveUser("mariana", Role.OPERATOR);
        saveUser("pedro", Role.OPERATOR);

        var page = adapter.findAll(new UserFilter("MARIA", null), byLogin());

        // the order is the database collation's, which this test is not about
        assertThat(page.items()).extracting(User::getLogin).containsExactlyInAnyOrder("maria.silva", "mariana");
        assertThat(adapter.findAll(new UserFilter("a.s", null), byLogin()).items())
                .extracting(User::getLogin).containsExactly("maria.silva");
    }

    @Test
    void shouldTreatLikeWildcardsInTheLoginFilterLiterally() {
        saveUser("100%admin", Role.ADMIN);
        saveUser("ana_maria", Role.OPERATOR);
        saveUser("anaxmaria", Role.OPERATOR);

        assertThat(adapter.findAll(new UserFilter("%", null), byLogin()).items())
                .extracting(User::getLogin).containsExactly("100%admin");
        assertThat(adapter.findAll(new UserFilter("_", null), byLogin()).items())
                .extracting(User::getLogin).containsExactly("ana_maria");
        assertThat(adapter.findAll(new UserFilter("!", null), byLogin()).items()).isEmpty();
    }

    @Test
    void shouldFilterByActive() {
        saveUser("ana", Role.ADMIN);
        var inactive = saveUser("bruno", Role.OPERATOR);
        inactive.deactivate();
        adapter.save(inactive);

        assertThat(adapter.findAll(new UserFilter(null, true), byLogin()).items())
                .extracting(User::getLogin).containsExactly("ana");
        assertThat(adapter.findAll(new UserFilter(null, false), byLogin()).items())
                .extracting(User::getLogin).containsExactly("bruno");
    }

    @Test
    void shouldCombineTheFiltersWithAnd() {
        saveUser("maria", Role.ADMIN);
        var inactive = saveUser("mariana", Role.OPERATOR);
        inactive.deactivate();
        adapter.save(inactive);

        assertThat(adapter.findAll(new UserFilter("maria", false), byLogin()).items())
                .extracting(User::getLogin).containsExactly("mariana");
        assertThat(adapter.findAll(new UserFilter("joao", true), byLogin()).items()).isEmpty();
    }

    @Test
    void shouldPaginateAndSortUsingPageQueryAndPageResult() {
        saveUser("ana", Role.ADMIN);
        saveUser("bruno", Role.OPERATOR);
        saveUser("carla", Role.OPERATOR);

        var second = adapter.findAll(UserFilter.none(), PageQuery.of(1, 2, "login,desc"));

        assertThat(second.items()).extracting(User::getLogin).containsExactly("ana");
        assertThat(second.page()).isEqualTo(1);
        assertThat(second.size()).isEqualTo(2);
        assertThat(second.totalItems()).isEqualTo(3);
    }

    @Test
    void shouldReturnAnEmptyPageWhenNothingMatches() {
        saveUser("ana", Role.ADMIN);

        var page = adapter.findAll(new UserFilter("zzz", null), byLogin());

        assertThat(page.items()).isEmpty();
        assertThat(page.totalItems()).isZero();
    }
}
