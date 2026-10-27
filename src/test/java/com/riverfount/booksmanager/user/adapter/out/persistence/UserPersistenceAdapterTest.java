package com.riverfount.booksmanager.user.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.PersistenceTest;
import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import com.riverfount.booksmanager.user.application.port.out.UserFilter;
import com.riverfount.booksmanager.user.domain.Reader;
import com.riverfount.booksmanager.user.domain.Role;
import com.riverfount.booksmanager.user.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Tests the User persistence adapter against a real PostgreSQL
 * (Testcontainers): the mapping between the domain and the JPA entity, the
 * unique login (RF08, RF09) and the listing filters.
 */
@PersistenceTest
class UserPersistenceAdapterTest {

    private static final String HASH = "$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5BzqhZ8H9V5xQe4Fq0sDq6Z0yR3Wm";

    @Autowired
    private UserPersistenceAdapter adapter;

    @Autowired
    private ReaderPersistenceAdapter readers;

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
    void shouldPersistTheDisplayNameAndLetItBeChangedOrCleared() {
        var saved = adapter.save(User.create("maria", HASH, Role.OPERATOR, "Maria Silva"));
        assertThat(adapter.findById(saved.getId()).orElseThrow().getDisplayName()).isEqualTo("Maria Silva");

        saved.changeDisplayName("Maria S.");
        adapter.save(saved);
        assertThat(adapter.findById(saved.getId()).orElseThrow().getDisplayName()).isEqualTo("Maria S.");

        saved.changeDisplayName(null);
        adapter.save(saved);
        assertThat(adapter.findById(saved.getId()).orElseThrow().getDisplayName()).isNull();
    }

    @Test
    void aUserWithoutDisplayNameShouldComeBackWithoutOne() {
        var saved = saveUser("joao", Role.OPERATOR);

        assertThat(adapter.findById(saved.getId()).orElseThrow().getDisplayName()).isNull();
    }

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
        assertThat(found.canAuthenticate(java.time.Instant.now())).isFalse();
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

    // login lockout (RF08)

    private static final java.time.Instant BLOCKED_UNTIL = java.time.Instant.parse("2026-10-15T12:15:00Z");

    @Test
    void shouldPersistAndReadBackTheLockoutState() {
        var saved = saveUser("maria", Role.OPERATOR);
        var locked = User.reconstitute(saved.getId(), "maria", HASH, Role.OPERATOR, true, 3, BLOCKED_UNTIL);
        adapter.save(locked);

        var found = adapter.findById(saved.getId()).orElseThrow();

        assertThat(found.getFailedLoginAttempts()).isEqualTo(3);
        assertThat(found.getBlockedUntil()).isEqualTo(BLOCKED_UNTIL);
        assertThat(jdbcTemplate.queryForObject("select failed_login_attempts from app_user where login = 'maria'",
                Integer.class)).isEqualTo(3);
    }

    @Test
    void shouldClearTheLockoutWhenTheUserIsSavedUnblocked() {
        var saved = saveUser("maria", Role.OPERATOR);
        adapter.save(User.reconstitute(saved.getId(), "maria", HASH, Role.OPERATOR, true, 3, BLOCKED_UNTIL));
        var found = adapter.findById(saved.getId()).orElseThrow();
        found.unblock();
        adapter.save(found);

        var again = adapter.findById(saved.getId()).orElseThrow();

        assertThat(again.getFailedLoginAttempts()).isZero();
        assertThat(again.getBlockedUntil()).isNull();
    }

    @Test
    void anewUserStartsWithNoFailedAttemptsAndNotBlocked() {
        var saved = saveUser("maria", Role.OPERATOR);

        assertThat(saved.getFailedLoginAttempts()).isZero();
        assertThat(saved.getBlockedUntil()).isNull();
    }

    @Test
    void findByLoginForAuthenticationShouldIgnoreCaseAndGiveTheLockoutState() {
        var saved = saveUser("maria", Role.OPERATOR);
        adapter.save(User.reconstitute(saved.getId(), "maria", HASH, Role.OPERATOR, true, 2, null));

        var found = adapter.findByLoginForAuthentication("  MARIA ").orElseThrow();

        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(found.getFailedLoginAttempts()).isEqualTo(2);
    }

    @Test
    void findByLoginForAuthenticationShouldReturnEmptyForAnUnknownOrMissingLogin() {
        saveUser("maria", Role.OPERATOR);

        assertThat(adapter.findByLoginForAuthentication("joao")).isEmpty();
        assertThat(adapter.findByLoginForAuthentication(null)).isEmpty();
        assertThat(adapter.findByLoginForAuthentication("")).isEmpty();
    }

    // loading for a change, with the row locked (RF09)

    @Test
    void shouldFindAUserByIdForUpdateWithTheSameState() {
        var saved = saveUser("maria", Role.OPERATOR);

        var found = adapter.findByIdForUpdate(saved.getId()).orElseThrow();

        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(found.getLogin()).isEqualTo("maria");
        assertThat(found.getRole()).isEqualTo(Role.OPERATOR);
        assertThat(adapter.findByIdForUpdate(999_999L)).isEmpty();
    }

    @Test
    void shouldFindOnlyTheActiveUsersOfTheProfileOrderedById() {
        var second = saveUser("zeca", Role.ADMIN);
        var first = saveUser("ana", Role.ADMIN);
        saveUser("operador", Role.OPERATOR);
        var inactive = saveUser("antigo", Role.ADMIN);
        inactive.deactivate();
        adapter.save(inactive);

        var admins = adapter.findActiveByRoleForUpdate(Role.ADMIN);

        assertThat(admins).extracting(User::getLogin).containsExactly("zeca", "ana");
        assertThat(admins).extracting(User::getId).containsExactly(second.getId(), first.getId());
        assertThat(adapter.findActiveByRoleForUpdate(Role.OPERATOR)).extracting(User::getLogin)
                .containsExactly("operador");
    }

    @Test
    void aBlockedAdministratorIsStillFoundSinceTheLockoutDoesNotChangeTheProfile() {
        var saved = saveUser("chefe", Role.ADMIN);
        adapter.save(User.reconstitute(saved.getId(), "chefe", HASH, Role.ADMIN, true, 3, BLOCKED_UNTIL));

        assertThat(adapter.findActiveByRoleForUpdate(Role.ADMIN)).extracting(User::getLogin).containsExactly("chefe");
    }

    @Test
    void shouldReturnAnEmptyListWhenNobodyHasTheProfile() {
        saveUser("operador", Role.OPERATOR);

        assertThat(adapter.findActiveByRoleForUpdate(Role.ADMIN)).isEmpty();
    }

    @Test
    void shouldPersistAChangeOfProfileMadeOnAUserLoadedForUpdate() {
        var saved = saveUser("maria", Role.OPERATOR);
        var loaded = adapter.findByIdForUpdate(saved.getId()).orElseThrow();
        loaded.changeRole(Role.ADMIN);
        adapter.save(loaded);

        assertThat(adapter.findById(saved.getId()).orElseThrow().getRole()).isEqualTo(Role.ADMIN);
        assertThat(jdbcTemplate.queryForObject("select role from app_user where login = 'maria'", String.class))
                .isEqualTo("ADMIN");
    }

    // reader access (Phase 6)

    private Long saveReader(String document) {
        return readers.save(Reader.create("Ana Souza", document, "ana@example.com", null,
                java.time.LocalDate.of(1990, 5, 10), null, java.time.LocalDate.of(2026, 10, 26))).getId();
    }

    @Test
    void shouldPersistTheReaderOfAReaderAccess() {
        var readerId = saveReader("RA-1");

        var saved = adapter.save(User.createReaderAccess("ana", HASH, readerId));

        var found = adapter.findById(saved.getId()).orElseThrow();
        assertThat(found.getRole()).isEqualTo(Role.READER);
        assertThat(found.getReaderId()).isEqualTo(readerId);
        assertThat(adapter.findById(saveUser("maria", Role.OPERATOR).getId()).orElseThrow().getReaderId()).isNull();
    }

    @Test
    void shouldFindTheActiveAccessOfAReader() {
        var readerId = saveReader("RA-2");
        var saved = adapter.save(User.createReaderAccess("ana", HASH, readerId));

        assertThat(adapter.findActiveByReaderIdForUpdate(readerId)).get().extracting(User::getId)
                .isEqualTo(saved.getId());
        assertThat(adapter.findActiveByReaderIdForUpdate(readerId + 1000)).isEmpty();
    }

    @Test
    void shouldNotFindARevokedAccessAsActive() {
        var readerId = saveReader("RA-3");
        var saved = adapter.save(User.createReaderAccess("ana", HASH, readerId));
        saved.deactivate();
        adapter.save(saved);

        assertThat(adapter.findActiveByReaderIdForUpdate(readerId)).isEmpty();
    }

    @Test
    void shouldAllowANewAccessAfterTheFirstOneWasRevoked() {
        var readerId = saveReader("RA-4");
        var first = adapter.save(User.createReaderAccess("ana", HASH, readerId));
        first.deactivate();
        adapter.save(first);

        var second = adapter.save(User.createReaderAccess("ana2", HASH, readerId));

        assertThat(adapter.findActiveByReaderIdForUpdate(readerId)).get().extracting(User::getId)
                .isEqualTo(second.getId());
    }

    @Test
    void shouldRefuseASecondActiveAccessForTheSameReader() {
        var readerId = saveReader("RA-5");
        adapter.save(User.createReaderAccess("ana", HASH, readerId));

        assertThatThrownBy(() -> adapter.save(User.createReaderAccess("ana2", HASH, readerId)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("acesso");
    }

    // reader accesses are not "users of the system" (#305)

    private void saveReaderAccess(String login, String document) {
        adapter.save(User.createReaderAccess(login, HASH, saveReader(document)));
    }

    @Test
    void shouldLeaveTheReaderAccessesOutOfTheListingByDefault() {
        saveUser("ana", Role.ADMIN);
        saveUser("bruno", Role.OPERATOR);
        saveReaderAccess("carla", "LS-1");

        var page = adapter.findAll(UserFilter.none(), byLogin());

        assertThat(page.items()).extracting(User::getLogin).containsExactly("ana", "bruno");
        assertThat(page.totalItems()).isEqualTo(2);
    }

    @Test
    void shouldListOnlyTheReaderAccessesWhenTheRoleIsReader() {
        saveUser("ana", Role.ADMIN);
        saveReaderAccess("carla", "LS-2");
        saveReaderAccess("dora", "LS-3");

        var page = adapter.findAll(new UserFilter(null, null, Role.READER), byLogin());

        assertThat(page.items()).extracting(User::getLogin).containsExactly("carla", "dora");
        assertThat(page.totalItems()).isEqualTo(2);
    }

    @Test
    void shouldListOnlyTheGivenRole() {
        saveUser("ana", Role.ADMIN);
        saveUser("bruno", Role.OPERATOR);
        saveReaderAccess("carla", "LS-4");

        assertThat(adapter.findAll(new UserFilter(null, null, Role.OPERATOR), byLogin()).items())
                .extracting(User::getLogin).containsExactly("bruno");
        assertThat(adapter.findAll(new UserFilter(null, null, Role.ADMIN), byLogin()).items())
                .extracting(User::getLogin).containsExactly("ana");
    }

    @Test
    void shouldCombineTheRoleWithTheLoginAndTheActiveFilters() {
        saveReaderAccess("carla", "LS-5");
        saveReaderAccess("carlos", "LS-6");

        assertThat(adapter.findAll(new UserFilter("carla", true, Role.READER), byLogin()).items())
                .extracting(User::getLogin).containsExactly("carla");
        assertThat(adapter.findAll(new UserFilter("carla", false, Role.READER), byLogin()).items()).isEmpty();
        assertThat(adapter.findAll(new UserFilter("carla", true, null), byLogin()).items()).isEmpty();
    }
}
