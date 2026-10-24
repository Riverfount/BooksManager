package com.riverfount.booksmanager.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.audit.application.RecordedAudit;
import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import com.riverfount.booksmanager.shared.domain.InvalidCredentialsException;
import com.riverfount.booksmanager.user.application.port.in.AuthenticateUserCommand;
import com.riverfount.booksmanager.user.application.port.in.AuthenticatedUser;
import com.riverfount.booksmanager.user.application.port.in.CreateInitialAdminCommand;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import com.riverfount.booksmanager.security.application.port.in.RevokeRefreshTokensUseCase;
import com.riverfount.booksmanager.user.application.port.in.CreateUserCommand;
import com.riverfount.booksmanager.user.application.port.in.DeactivateUserCommand;
import com.riverfount.booksmanager.user.application.port.in.ListUsersQuery;
import com.riverfount.booksmanager.user.application.port.in.ResetUserPasswordCommand;
import com.riverfount.booksmanager.user.application.port.in.UpdateUserCommand;
import com.riverfount.booksmanager.user.application.port.in.InitialAdminResult;
import com.riverfount.booksmanager.user.application.port.out.PasswordHasher;
import com.riverfount.booksmanager.user.configuration.LoginLockoutProperties;
import com.riverfount.booksmanager.user.domain.Role;
import com.riverfount.booksmanager.user.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UserServiceTest {

    /** Stands for BCrypt: never returns the raw password and differs on each call, like a salted hash. */
    private static class FakePasswordHasher implements PasswordHasher {
        private int calls;
        private final java.util.List<String> hashesChecked = new java.util.ArrayList<>();

        @Override
        public String hash(String rawPassword) {
            return "{hash-" + (++calls) + "}" + new StringBuilder(rawPassword).reverse();
        }

        @Override
        public boolean matches(String rawPassword, String storedHash) {
            hashesChecked.add(storedHash);
            return rawPassword != null && storedHash != null
                    && storedHash.endsWith("}" + new StringBuilder(rawPassword).reverse());
        }
    }

    /** A clock the tests move forward by hand. */
    private static class MutableClock extends java.time.Clock {
        private java.time.Instant now;

        MutableClock(java.time.Instant now) {
            this.now = now;
        }

        void advance(java.time.Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public java.time.ZoneId getZone() {
            return java.time.ZoneOffset.UTC;
        }

        @Override
        public java.time.Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public java.time.Instant instant() {
            return now;
        }
    }

    private static final java.time.Instant START = java.time.Instant.parse("2026-10-15T12:00:00Z");
    private static final java.time.Duration BLOCK = java.time.Duration.ofMinutes(15);

    /** The ids whose refresh tokens the service asked the security module to revoke. */
    private final java.util.List<Long> revokedFor = new java.util.ArrayList<>();

    private InMemoryUserRepository repository;
    private FakePasswordHasher hasher;
    private MutableClock clock;
    private UserService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryUserRepository();
        hasher = new FakePasswordHasher();
        clock = new MutableClock(START);
        revokedFor.clear();
        RevokeRefreshTokensUseCase revoker = userId -> {
            revokedFor.add(userId);
            return 1;
        };
        service = new UserService(repository, hasher, clock, new LoginLockoutProperties(3, BLOCK), revoker,
                new RecordedAudit());
    }

    // initial administrator (RF08, RNF10)

    @Test
    void shouldCreateTheAdministratorWhenThereIsNoUser() {
        var result = service.createInitialAdmin(new CreateInitialAdminCommand("Admin", "S3cret-Pass!"));

        assertThat(result).isEqualTo(InitialAdminResult.CREATED);
        var admin = repository.findByLogin("admin").orElseThrow();
        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
        assertThat(admin.isActive()).isTrue();
        assertThat(admin.getLogin()).isEqualTo("admin");
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void shouldStoreOnlyTheHashOfThePassword() {
        service.createInitialAdmin(new CreateInitialAdminCommand("admin", "S3cret-Pass!"));

        var stored = repository.findByLogin("admin").orElseThrow().getPasswordHash();

        assertThat(stored).isEqualTo("{hash-1}!ssaP-terc3S");
        assertThat(stored).isNotEqualTo("S3cret-Pass!");
    }

    @Test
    void shouldNotCreateAnythingWhenThereIsAlreadyAUser() {
        repository.save(User.create("operator", "{existing}", Role.OPERATOR));

        var result = service.createInitialAdmin(new CreateInitialAdminCommand("admin", "S3cret-Pass!"));

        assertThat(result).isEqualTo(InitialAdminResult.ALREADY_HAS_USERS);
        assertThat(repository.count()).isEqualTo(1);
        assertThat(repository.existsByLogin("admin")).isFalse();
    }

    @Test
    void shouldNotHashThePasswordWhenNothingIsCreated() {
        repository.save(User.create("operator", "{existing}", Role.OPERATOR));

        service.createInitialAdmin(new CreateInitialAdminCommand("admin", "S3cret-Pass!"));

        assertThat(hasher.calls).isZero();
    }

    @Test
    void runningAgainShouldNeitherDuplicateNorChangeTheAdministrator() {
        service.createInitialAdmin(new CreateInitialAdminCommand("admin", "S3cret-Pass!"));
        var before = repository.findByLogin("admin").orElseThrow();

        var again = service.createInitialAdmin(new CreateInitialAdminCommand("admin", "Another-Pass1!"));
        var againWithOtherLogin = service.createInitialAdmin(new CreateInitialAdminCommand("other", "x"));

        assertThat(again).isEqualTo(InitialAdminResult.ALREADY_HAS_USERS);
        assertThat(againWithOtherLogin).isEqualTo(InitialAdminResult.ALREADY_HAS_USERS);
        var after = repository.findByLogin("admin").orElseThrow();
        assertThat(after.getId()).isEqualTo(before.getId());
        assertThat(after.getPasswordHash()).isEqualTo(before.getPasswordHash());
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void shouldNotCreateWhenTheLoginOrThePasswordIsMissing() {
        for (var command : new CreateInitialAdminCommand[] {
                new CreateInitialAdminCommand(null, null),
                new CreateInitialAdminCommand("", ""),
                new CreateInitialAdminCommand("admin", null),
                new CreateInitialAdminCommand("admin", "   "),
                new CreateInitialAdminCommand("  ", "S3cret-Pass!"),
                new CreateInitialAdminCommand(null, "S3cret-Pass!")}) {
            assertThat(service.createInitialAdmin(command)).isEqualTo(InitialAdminResult.NOT_CONFIGURED);
        }

        assertThat(repository.count()).isZero();
        assertThat(hasher.calls).isZero();
    }

    @Test
    void shouldRefuseAWeakPasswordWithoutCreatingAnythingNorShowingIt() {
        for (var weak : new String[] {"Sh1!", "alllowercase1!", "ALLUPPERCASE1!", "NoDigitsHere!", "NoSpecial123x",
                "Aa1!" + "x".repeat(30)}) {
            assertThatThrownBy(() -> service.createInitialAdmin(new CreateInitialAdminCommand("admin", weak)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("senha")
                    .hasMessageNotContaining(weak);
        }

        assertThat(repository.count()).isZero();
        assertThat(hasher.calls).isZero();
    }

    @Test
    void aWeakPasswordShouldNotMatterOnceThereIsAlreadyAUser() {
        repository.save(User.create("operator", "{existing}", Role.OPERATOR));

        var result = service.createInitialAdmin(new CreateInitialAdminCommand("admin", "weak"));

        assertThat(result).isEqualTo(InitialAdminResult.ALREADY_HAS_USERS);
    }

    @Test
    void shouldRefuseAnInvalidLoginWithoutCreatingAnything() {
        assertThatThrownBy(() -> service.createInitialAdmin(new CreateInitialAdminCommand("ad min", "S3cret-Pass!")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("login")
                .hasMessageNotContaining("S3cret-Pass!");

        assertThat(repository.count()).isZero();
    }

    @Test
    void theCommandShouldNotShowThePasswordInItsStringForm() {
        var command = new CreateInitialAdminCommand("admin", "S3cret-Pass!");

        assertThat(command.toString()).doesNotContain("S3cret-Pass!").contains("admin");
    }

    // authentication (RF08, HU05, RNF07)

    private static final String RIGHT_PASSWORD = "S3cret-Pass!";

    private void registerUser(String login, Role role) {
        repository.save(User.create(login, hasher.hash(RIGHT_PASSWORD), role));
    }

    private void deactivate(String login) {
        var user = repository.findByLogin(login).orElseThrow();
        user.deactivate();
        repository.save(user);
    }

    private static String messageOf(Runnable attempt) {
        try {
            attempt.run();
        } catch (InvalidCredentialsException e) {
            return e.getMessage();
        }
        throw new AssertionError("expected InvalidCredentialsException");
    }

    @Test
    void shouldAuthenticateWithTheRightCredentialsAndGiveTheIdAndTheRole() {
        registerUser("maria", Role.OPERATOR);
        registerUser("chefe", Role.ADMIN);

        var maria = service.authenticate(new AuthenticateUserCommand("maria", RIGHT_PASSWORD));
        var chefe = service.authenticate(new AuthenticateUserCommand("chefe", RIGHT_PASSWORD));

        assertThat(maria).isEqualTo(new AuthenticatedUser(repository.findByLogin("maria").orElseThrow().getId(),
                Role.OPERATOR));
        assertThat(chefe.role()).isEqualTo(Role.ADMIN);
    }

    @Test
    void theLoginShouldNotBeCaseSensitive() {
        registerUser("maria", Role.OPERATOR);

        assertThat(service.authenticate(new AuthenticateUserCommand("  MARIA ", RIGHT_PASSWORD)).role())
                .isEqualTo(Role.OPERATOR);
    }

    @Test
    void shouldRefuseAWrongPassword() {
        registerUser("maria", Role.OPERATOR);

        assertThatThrownBy(() -> service.authenticate(new AuthenticateUserCommand("maria", "Wrong-Pass1!")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void shouldRefuseAnUnknownLogin() {
        assertThatThrownBy(() -> service.authenticate(new AuthenticateUserCommand("nobody", RIGHT_PASSWORD)))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void shouldRefuseAnInactiveUserEvenWithTheRightPassword() {
        registerUser("maria", Role.OPERATOR);
        deactivate("maria");

        assertThatThrownBy(() -> service.authenticate(new AuthenticateUserCommand("maria", RIGHT_PASSWORD)))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void everyFailureShouldHaveTheSameMessageSoNothingTellsWhichPartWasWrong() {
        registerUser("maria", Role.OPERATOR);
        registerUser("inativa", Role.OPERATOR);
        deactivate("inativa");

        var wrongPassword = messageOf(() -> service.authenticate(new AuthenticateUserCommand("maria", "Wrong-Pass1!")));
        var unknownLogin = messageOf(() -> service.authenticate(new AuthenticateUserCommand("nobody", RIGHT_PASSWORD)));
        var inactive = messageOf(() -> service.authenticate(new AuthenticateUserCommand("inativa", RIGHT_PASSWORD)));

        assertThat(wrongPassword).isEqualTo(unknownLogin).isEqualTo(inactive);
        assertThat(wrongPassword).doesNotContain("maria").doesNotContain("nobody").doesNotContain(RIGHT_PASSWORD);
    }

    @Test
    void shouldCheckAPasswordEvenWhenTheLoginDoesNotExistSoTheTimeDoesNotRevealWhichLoginsExist() {
        registerUser("maria", Role.OPERATOR);
        var hashOfMaria = repository.findByLogin("maria").orElseThrow().getPasswordHash();

        messageOf(() -> service.authenticate(new AuthenticateUserCommand("nobody", RIGHT_PASSWORD)));

        assertThat(hasher.hashesChecked).hasSize(1);
        assertThat(hasher.hashesChecked.get(0)).isNotBlank().isNotEqualTo(hashOfMaria);
    }

    @Test
    void shouldCheckThePasswordAlsoForAnInactiveUserSoItCostsTheSame() {
        registerUser("maria", Role.OPERATOR);
        deactivate("maria");

        messageOf(() -> service.authenticate(new AuthenticateUserCommand("maria", RIGHT_PASSWORD)));

        assertThat(hasher.hashesChecked).hasSize(1);
    }

    @Test
    void shouldMakeTheFakeHashOnlyOnceNoMatterHowManyUnknownLogins() {
        var before = hasher.calls;

        for (var login : new String[] {"nobody1", "nobody2", "nobody3"}) {
            messageOf(() -> service.authenticate(new AuthenticateUserCommand(login, RIGHT_PASSWORD)));
        }

        assertThat(hasher.calls - before).isEqualTo(1);
        assertThat(hasher.hashesChecked).hasSize(3).containsOnly(hasher.hashesChecked.get(0));
    }

    @Test
    void shouldRefuseMissingCredentialsWithoutFailingForAnotherReason() {
        registerUser("maria", Role.OPERATOR);

        for (var command : new AuthenticateUserCommand[] {
                new AuthenticateUserCommand(null, null),
                new AuthenticateUserCommand("", ""),
                new AuthenticateUserCommand("maria", null),
                new AuthenticateUserCommand("maria", "  "),
                new AuthenticateUserCommand(null, RIGHT_PASSWORD)}) {
            assertThatThrownBy(() -> service.authenticate(command)).isInstanceOf(InvalidCredentialsException.class);
        }
    }

    @Test
    void theLoginCommandShouldNotShowThePasswordInItsStringForm() {
        assertThat(new AuthenticateUserCommand("maria", RIGHT_PASSWORD).toString())
                .doesNotContain(RIGHT_PASSWORD).contains("maria");
    }

    @Test
    void theAuthenticatedUserShouldCarryNoSecret() {
        assertThat(AuthenticatedUser.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .containsExactly("id", "role");
    }

    // login lockout after wrong passwords (RF08)

    private static final String WRONG = "Wrong-Pass1!";

    private void wrongPassword(String login, int times) {
        for (var i = 0; i < times; i++) {
            messageOf(() -> service.authenticate(new AuthenticateUserCommand(login, WRONG)));
        }
    }

    private boolean canLogIn(String login) {
        try {
            service.authenticate(new AuthenticateUserCommand(login, RIGHT_PASSWORD));
            return true;
        } catch (InvalidCredentialsException e) {
            return false;
        }
    }

    @Test
    void shouldBlockTheUserAfterThreeWrongPasswordsEvenForTheRightPasswordNext() {
        registerUser("maria", Role.OPERATOR);

        wrongPassword("maria", 3);

        assertThat(repository.findByLogin("maria").orElseThrow().isBlocked(START)).isTrue();
        assertThat(canLogIn("maria")).isFalse();
    }

    @Test
    void shouldNotBlockBeforeTheThirdWrongPassword() {
        registerUser("maria", Role.OPERATOR);

        wrongPassword("maria", 2);

        assertThat(repository.findByLogin("maria").orElseThrow().getFailedLoginAttempts()).isEqualTo(2);
        assertThat(canLogIn("maria")).isTrue();
    }

    @Test
    void aBlockedUserShouldGetExactlyTheSameRefusalAsAWrongPasswordOrAnUnknownLogin() {
        registerUser("maria", Role.OPERATOR);
        wrongPassword("maria", 3);

        var blockedWithRightPassword = messageOf(() -> service.authenticate(
                new AuthenticateUserCommand("maria", RIGHT_PASSWORD)));
        var blockedWithWrongPassword = messageOf(() -> service.authenticate(new AuthenticateUserCommand("maria", WRONG)));
        var unknown = messageOf(() -> service.authenticate(new AuthenticateUserCommand("nobody", WRONG)));

        assertThat(blockedWithRightPassword).isEqualTo(blockedWithWrongPassword).isEqualTo(unknown);
        assertThat(blockedWithRightPassword).doesNotContain("bloque").doesNotContain("tentativa");
    }

    @Test
    void aCorrectPasswordBeforeTheLimitShouldResetTheCount() {
        registerUser("maria", Role.OPERATOR);
        wrongPassword("maria", 2);

        assertThat(canLogIn("maria")).isTrue();
        assertThat(repository.findByLogin("maria").orElseThrow().getFailedLoginAttempts()).isZero();

        wrongPassword("maria", 2);
        assertThat(canLogIn("maria")).isTrue();
    }

    @Test
    void theBlockShouldEndAfterTheConfiguredTimeAndTheRightPasswordWorkAgain() {
        registerUser("maria", Role.OPERATOR);
        wrongPassword("maria", 3);

        clock.advance(BLOCK.minusSeconds(1));
        assertThat(canLogIn("maria")).isFalse();

        clock.advance(java.time.Duration.ofSeconds(1));
        assertThat(canLogIn("maria")).isTrue();
        var user = repository.findByLogin("maria").orElseThrow();
        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getBlockedUntil()).isNull();
    }

    @Test
    void wrongPasswordsWhileBlockedShouldNotExtendTheBlock() {
        registerUser("maria", Role.OPERATOR);
        wrongPassword("maria", 3);
        var until = repository.findByLogin("maria").orElseThrow().getBlockedUntil();

        clock.advance(java.time.Duration.ofMinutes(10));
        wrongPassword("maria", 5);

        assertThat(repository.findByLogin("maria").orElseThrow().getBlockedUntil()).isEqualTo(until);
        clock.advance(java.time.Duration.ofMinutes(5));
        assertThat(canLogIn("maria")).isTrue();
    }

    @Test
    void afterTheBlockEndsItShouldTakeAFullNewRoundOfWrongPasswordsToBlockAgain() {
        registerUser("maria", Role.OPERATOR);
        wrongPassword("maria", 3);
        clock.advance(BLOCK);

        wrongPassword("maria", 2);
        assertThat(repository.findByLogin("maria").orElseThrow().isBlocked(clock.instant())).isFalse();

        wrongPassword("maria", 1);
        assertThat(repository.findByLogin("maria").orElseThrow().isBlocked(clock.instant())).isTrue();
    }

    @Test
    void wrongPasswordsOfAnInactiveUserShouldNotBeCounted() {
        registerUser("maria", Role.OPERATOR);
        deactivate("maria");

        wrongPassword("maria", 5);

        var user = repository.findByLogin("maria").orElseThrow();
        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getBlockedUntil()).isNull();
    }

    @Test
    void wrongPasswordsOfAnUnknownLoginShouldLeaveNoTrace() {
        var savesBefore = repository.saves();

        wrongPassword("nobody", 5);

        assertThat(repository.count()).isZero();
        assertThat(repository.saves()).isEqualTo(savesBefore);
    }

    @Test
    void theLockoutShouldBeOfEachUserAlone() {
        registerUser("maria", Role.OPERATOR);
        registerUser("joao", Role.OPERATOR);

        wrongPassword("maria", 3);

        assertThat(canLogIn("maria")).isFalse();
        assertThat(canLogIn("joao")).isTrue();
    }

    @Test
    void thePasswordShouldStillBeCheckedForABlockedUserSoTheTimeStaysTheSame() {
        registerUser("maria", Role.OPERATOR);
        wrongPassword("maria", 3);
        var checksBefore = hasher.hashesChecked.size();

        canLogIn("maria");

        assertThat(hasher.hashesChecked.size() - checksBefore).isEqualTo(1);
    }

    @Test
    void aLoginWithNothingToResetShouldNotWriteTheUser() {
        registerUser("maria", Role.OPERATOR);
        var savesBefore = repository.saves();

        canLogIn("maria");

        assertThat(repository.saves()).isEqualTo(savesBefore);
    }

    @Test
    void shouldHonorAnotherLimitAndDuration() {
        var strict = new UserService(repository, hasher, clock, new LoginLockoutProperties(1, java.time.Duration.ofHours(1)),
                userId -> 0, new RecordedAudit());
        registerUser("maria", Role.OPERATOR);

        messageOf(() -> strict.authenticate(new AuthenticateUserCommand("maria", WRONG)));

        assertThat(repository.findByLogin("maria").orElseThrow().getBlockedUntil())
                .isEqualTo(START.plus(java.time.Duration.ofHours(1)));
    }

    // creating a user (RF09, HU28)

    private static final String GOOD_PASSWORD = "N3w-Pass#word";

    // display name

    @Test
    void shouldCreateAUserWithTheDisplayName() {
        var created = service.create(new CreateUserCommand("maria", GOOD_PASSWORD, Role.OPERATOR, " Maria Silva "));

        assertThat(created.getDisplayName()).isEqualTo("Maria Silva");
        assertThat(repository.findById(created.getId()).orElseThrow().getDisplayName()).isEqualTo("Maria Silva");
    }

    @Test
    void shouldCreateAUserWithoutDisplayName() {
        var created = service.create(new CreateUserCommand("maria", GOOD_PASSWORD, Role.OPERATOR));

        assertThat(created.getDisplayName()).isNull();
    }

    @Test
    void shouldRefuseATooLongDisplayNameWithoutCreatingTheUser() {
        var tooLong = "a".repeat(User.MAX_DISPLAY_NAME_LENGTH + 1);

        assertThatThrownBy(() -> service.create(new CreateUserCommand("maria", GOOD_PASSWORD, Role.OPERATOR,
                tooLong))).isInstanceOf(BusinessRuleException.class);
        assertThat(repository.count()).isZero();
    }

    @Test
    void shouldChangeOnlyTheDisplayNameKeepingTheRole() {
        var created = service.create(new CreateUserCommand("chefe", GOOD_PASSWORD, Role.ADMIN, "Chefe"));

        var updated = service.update(new UpdateUserCommand(created.getId(), null, "Carlos Mendes"));

        assertThat(updated.getDisplayName()).isEqualTo("Carlos Mendes");
        assertThat(updated.getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    void shouldChangeOnlyTheRoleKeepingTheDisplayName() {
        service.create(new CreateUserCommand("chefe", GOOD_PASSWORD, Role.ADMIN));
        var created = service.create(new CreateUserCommand("maria", GOOD_PASSWORD, Role.OPERATOR, "Maria"));

        var updated = service.update(new UpdateUserCommand(created.getId(), Role.ADMIN, null));

        assertThat(updated.getRole()).isEqualTo(Role.ADMIN);
        assertThat(updated.getDisplayName()).isEqualTo("Maria");
    }

    @Test
    void aBlankDisplayNameInTheUpdateShouldClearIt() {
        var created = service.create(new CreateUserCommand("maria", GOOD_PASSWORD, Role.OPERATOR, "Maria"));

        var updated = service.update(new UpdateUserCommand(created.getId(), null, "  "));

        assertThat(updated.getDisplayName()).isNull();
    }

    @Test
    void shouldCreateAnActiveUserWithTheProfileAndAnId() {
        var created = service.create(new CreateUserCommand("Maria", GOOD_PASSWORD, Role.OPERATOR));

        assertThat(created.getId()).isNotNull();
        assertThat(created.getLogin()).isEqualTo("maria");
        assertThat(created.getRole()).isEqualTo(Role.OPERATOR);
        assertThat(created.isActive()).isTrue();
        assertThat(created.getFailedLoginAttempts()).isZero();
        assertThat(repository.findByLogin("maria")).isPresent();
    }

    @Test
    void shouldAlsoCreateAnAdministrator() {
        assertThat(service.create(new CreateUserCommand("chefe", GOOD_PASSWORD, Role.ADMIN)).getRole())
                .isEqualTo(Role.ADMIN);
    }

    @Test
    void aCreatedUserShouldKeepOnlyTheHashOfThePassword() {
        var created = service.create(new CreateUserCommand("maria", GOOD_PASSWORD, Role.OPERATOR));

        var stored = repository.findByLogin("maria").orElseThrow().getPasswordHash();
        assertThat(stored).isEqualTo(created.getPasswordHash()).isNotEqualTo(GOOD_PASSWORD);
        assertThat(stored).doesNotContain(GOOD_PASSWORD);
        assertThat(stored).startsWith("{hash-");
    }

    @Test
    void theNewUserShouldBeAbleToLogInWithThePassword() {
        service.create(new CreateUserCommand("maria", GOOD_PASSWORD, Role.OPERATOR));

        var authenticated = service.authenticate(new AuthenticateUserCommand("maria", GOOD_PASSWORD));

        assertThat(authenticated.role()).isEqualTo(Role.OPERATOR);
    }

    @Test
    void shouldRefuseARepeatedLoginAlsoWhenOnlyTheCaseOrTheSpacesDiffer() {
        service.create(new CreateUserCommand("maria", GOOD_PASSWORD, Role.OPERATOR));

        for (var login : new String[] {"maria", "MARIA", "Maria"}) {
            assertThatThrownBy(() -> service.create(new CreateUserCommand(login, GOOD_PASSWORD, Role.ADMIN)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("já existe um usuário");
        }

        assertThat(repository.count()).isEqualTo(1);
        assertThat(repository.findByLogin("maria").orElseThrow().getRole()).isEqualTo(Role.OPERATOR);
    }

    @Test
    void shouldRefuseAWeakPasswordNamingTheMissingRulesAndNeverRepeatingIt() {
        for (var weak : new String[] {"Sh0rt!", "alllowercase1!", "ALLUPPERCASE1!", "NoDigitsHere!", "NoSpecial1234",
                "Aa1!" + "x".repeat(30)}) {
            assertThatThrownBy(() -> service.create(new CreateUserCommand("maria", weak, Role.OPERATOR)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("senha")
                    .hasMessageNotContaining(weak);
        }

        assertThat(repository.count()).isZero();
    }

    @Test
    void shouldNotHashThePasswordWhenItIsAlreadyKnownToBeWeak() {
        var before = hasher.calls;

        assertThatThrownBy(() -> service.create(new CreateUserCommand("maria", "weak", Role.OPERATOR)))
                .isInstanceOf(BusinessRuleException.class);

        assertThat(hasher.calls).isEqualTo(before);
    }

    @Test
    void shouldRefuseAnInvalidLoginOrAMissingProfile() {
        assertThatThrownBy(() -> service.create(new CreateUserCommand("ma ria", GOOD_PASSWORD, Role.OPERATOR)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("login");
        assertThatThrownBy(() -> service.create(new CreateUserCommand(null, GOOD_PASSWORD, Role.OPERATOR)))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.create(new CreateUserCommand("maria", GOOD_PASSWORD, null)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("perfil");
        assertThatThrownBy(() -> service.create(new CreateUserCommand("maria", null, Role.OPERATOR)))
                .isInstanceOf(BusinessRuleException.class);

        assertThat(repository.count()).isZero();
    }

    @Test
    void theCreateCommandShouldNotShowThePasswordInItsStringForm() {
        assertThat(new CreateUserCommand("maria", GOOD_PASSWORD, Role.OPERATOR).toString())
                .doesNotContain(GOOD_PASSWORD).contains("maria").contains("OPERATOR");
    }

    // querying users (RF09, HU28, RNF14)

    private static ListUsersQuery usersQuery(String login, boolean active) {
        return new ListUsersQuery(login, active, PageQuery.of(0, 20, "login"));
    }

    private Long userId(String login) {
        return repository.findByLogin(login).orElseThrow().getId();
    }

    @Test
    void shouldFindAUserByIdIncludingAnInactiveOneAndOneThatIsBlocked() {
        registerUser("maria", Role.OPERATOR);
        registerUser("joao", Role.OPERATOR);
        registerUser("bloqueada", Role.OPERATOR);
        deactivate("joao");
        wrongPassword("bloqueada", 3);

        assertThat(service.findById(userId("maria"))).isPresent();
        assertThat(service.findById(userId("joao")).orElseThrow().isActive()).isFalse();
        assertThat(service.findById(userId("bloqueada")).orElseThrow().isBlocked(clock.instant())).isTrue();
    }

    @Test
    void shouldReturnEmptyForAnUnknownIdAndRefuseANullOne() {
        assertThat(service.findById(99_999L)).isEmpty();
        assertThatThrownBy(() -> service.findById(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldListOnlyActiveUsersByDefaultAndTheInactiveOnesWhenAsked() {
        registerUser("ana", Role.OPERATOR);
        registerUser("bruno", Role.OPERATOR);
        deactivate("bruno");

        assertThat(service.list(usersQuery(null, true)).items()).extracting(User::getLogin).containsExactly("ana");
        assertThat(service.list(usersQuery(null, false)).items()).extracting(User::getLogin)
                .containsExactly("bruno");
    }

    @Test
    void shouldFilterByPartOfTheLoginIgnoringCase() {
        registerUser("maria.silva", Role.OPERATOR);
        registerUser("mariana", Role.OPERATOR);
        registerUser("pedro", Role.ADMIN);

        assertThat(service.list(usersQuery("MARIA", true)).items()).extracting(User::getLogin)
                .containsExactlyInAnyOrder("maria.silva", "mariana");
        assertThat(service.list(usersQuery("edr", true)).items()).extracting(User::getLogin).containsExactly("pedro");
        assertThat(service.list(usersQuery("zzz", true)).items()).isEmpty();
    }

    @Test
    void shouldPaginateAndSortByLoginDescendingOrById() {
        registerUser("ana", Role.OPERATOR);
        registerUser("bruno", Role.OPERATOR);
        registerUser("carla", Role.OPERATOR);

        var second = service.list(new ListUsersQuery(null, true, PageQuery.of(1, 2, "login,desc")));
        var byId = service.list(new ListUsersQuery(null, true, PageQuery.of(0, 20, "id")));

        assertThat(second.items()).extracting(User::getLogin).containsExactly("ana");
        assertThat(second.totalItems()).isEqualTo(3);
        assertThat(second.totalPages()).isEqualTo(2);
        assertThat(byId.items()).extracting(User::getLogin).containsExactly("ana", "bruno", "carla");
    }

    @Test
    void theListedUsersShouldCarryTheLockoutStateSoTheAdministratorCanSeeWhoIsBlocked() {
        registerUser("ana", Role.OPERATOR);
        registerUser("bloqueada", Role.OPERATOR);
        wrongPassword("bloqueada", 3);

        var users = service.list(usersQuery(null, true)).items();

        assertThat(users).filteredOn(User::isActive).hasSize(2);
        assertThat(users.stream().filter(user -> user.isBlocked(clock.instant())).map(User::getLogin))
                .containsExactly("bloqueada");
    }

    // editing the profile and unblocking (RF09, HU28)

    private User stored(String login) {
        return repository.findByLogin(login).orElseThrow();
    }

    @Test
    void shouldPromoteAnOperatorAndChangeNothingElse() {
        registerUser("maria", Role.OPERATOR);
        var before = stored("maria");

        var updated = service.update(new UpdateUserCommand(before.getId(), Role.ADMIN));

        assertThat(updated.getRole()).isEqualTo(Role.ADMIN);
        assertThat(stored("maria").getRole()).isEqualTo(Role.ADMIN);
        assertThat(stored("maria").getLogin()).isEqualTo("maria");
        assertThat(stored("maria").getPasswordHash()).isEqualTo(before.getPasswordHash());
        assertThat(stored("maria").isActive()).isTrue();
    }

    @Test
    void shouldKeepTheProfileWhenNoneIsSent() {
        registerUser("maria", Role.OPERATOR);

        var updated = service.update(new UpdateUserCommand(stored("maria").getId(), null));

        assertThat(updated.getRole()).isEqualTo(Role.OPERATOR);
    }

    @Test
    void shouldDemoteAnAdministratorWhenAnotherActiveOneRemains() {
        registerUser("chefe", Role.ADMIN);
        registerUser("vice", Role.ADMIN);

        service.update(new UpdateUserCommand(stored("chefe").getId(), Role.OPERATOR));

        assertThat(stored("chefe").getRole()).isEqualTo(Role.OPERATOR);
        assertThat(stored("vice").getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    void shouldRefuseToDemoteTheLastActiveAdministrator() {
        registerUser("chefe", Role.ADMIN);
        registerUser("maria", Role.OPERATOR);
        var id = stored("chefe").getId();

        assertThatThrownBy(() -> service.update(new UpdateUserCommand(id, Role.OPERATOR)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("último administrador");

        assertThat(stored("chefe").getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    void anInactiveAdministratorDoesNotCountAsTheOneWhoRemains() {
        registerUser("chefe", Role.ADMIN);
        registerUser("antigo", Role.ADMIN);
        deactivate("antigo");
        var id = stored("chefe").getId();

        assertThatThrownBy(() -> service.update(new UpdateUserCommand(id, Role.OPERATOR)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void aBlockedAdministratorStillCountsSoTheLockoutDoesNotStopTheRule() {
        registerUser("chefe", Role.ADMIN);
        registerUser("vice", Role.ADMIN);
        wrongPassword("vice", 3);

        service.update(new UpdateUserCommand(stored("chefe").getId(), Role.OPERATOR));

        assertThat(stored("chefe").getRole()).isEqualTo(Role.OPERATOR);
    }

    @Test
    void keepingTheProfileOfTheLastAdministratorIsNotAnError() {
        registerUser("chefe", Role.ADMIN);

        var updated = service.update(new UpdateUserCommand(stored("chefe").getId(), Role.ADMIN));

        assertThat(updated.getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    void demotingAnInactiveAdministratorIsAllowedEvenIfItIsTheOnlyAdministrator() {
        registerUser("antigo", Role.ADMIN);
        deactivate("antigo");

        service.update(new UpdateUserCommand(stored("antigo").getId(), Role.OPERATOR));

        assertThat(stored("antigo").getRole()).isEqualTo(Role.OPERATOR);
    }

    @Test
    void shouldRejectTheEditOfAnUnknownUser() {
        assertThatThrownBy(() -> service.update(new UpdateUserCommand(99_999L, Role.ADMIN)))
                .isInstanceOf(com.riverfount.booksmanager.shared.domain.ResourceNotFoundException.class)
                .hasMessageContaining("99999");
    }

    @Test
    void theEditCommandShouldCarryOnlyTheProfileAndTheDisplayName() {
        // never the login, the password or the activation
        assertThat(UpdateUserCommand.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .containsExactly("id", "role", "displayName");
    }

    @Test
    void unblockShouldLetTheRightPasswordInAtOnce() {
        registerUser("maria", Role.OPERATOR);
        wrongPassword("maria", 3);
        assertThat(canLogIn("maria")).isFalse();

        service.unblock(stored("maria").getId());

        assertThat(stored("maria").getFailedLoginAttempts()).isZero();
        assertThat(stored("maria").getBlockedUntil()).isNull();
        assertThat(canLogIn("maria")).isTrue();
    }

    @Test
    void unblockingAUserWhoIsNotBlockedChangesNothingAndWritesNothing() {
        registerUser("maria", Role.OPERATOR);
        var savesBefore = repository.saves();

        service.unblock(stored("maria").getId());
        service.unblock(stored("maria").getId());

        assertThat(repository.saves()).isEqualTo(savesBefore);
        assertThat(canLogIn("maria")).isTrue();
    }

    @Test
    void unblockShouldClearWrongPasswordsThatDidNotReachTheLimitToo() {
        registerUser("maria", Role.OPERATOR);
        wrongPassword("maria", 2);

        service.unblock(stored("maria").getId());

        assertThat(stored("maria").getFailedLoginAttempts()).isZero();
    }

    @Test
    void unblockShouldKeepAnInactiveUserInactive() {
        registerUser("maria", Role.OPERATOR);
        wrongPassword("maria", 1);
        deactivate("maria");

        service.unblock(stored("maria").getId());

        assertThat(stored("maria").isActive()).isFalse();
        assertThat(canLogIn("maria")).isFalse();
    }

    @Test
    void shouldRejectTheUnblockOfAnUnknownUser() {
        assertThatThrownBy(() -> service.unblock(99_999L))
                .isInstanceOf(com.riverfount.booksmanager.shared.domain.ResourceNotFoundException.class);
    }

    // deactivating a user (RF09, HU28, RN14)

    private DeactivateUserCommand deactivation(String login, Long actingUserId) {
        return new DeactivateUserCommand(stored(login).getId(), actingUserId);
    }

    private static final Long SOMEONE_ELSE = 9_999L;

    @Test
    void shouldDeactivateTheUserKeepingTheRecordAndRevokeItsRefreshTokens() {
        registerUser("maria", Role.OPERATOR);
        var before = stored("maria");

        service.deactivate(deactivation("maria", SOMEONE_ELSE));

        var after = stored("maria");
        assertThat(after.isActive()).isFalse();
        assertThat(after.getLogin()).isEqualTo("maria");
        assertThat(after.getRole()).isEqualTo(Role.OPERATOR);
        assertThat(after.getPasswordHash()).isEqualTo(before.getPasswordHash());
        assertThat(revokedFor).containsExactly(before.getId());
    }

    @Test
    void anInactiveUserCannotLogInAnymore() {
        registerUser("maria", Role.OPERATOR);
        service.deactivate(deactivation("maria", SOMEONE_ELSE));

        assertThat(canLogIn("maria")).isFalse();
    }

    @Test
    void deactivatingAnInactiveUserAgainIsIdempotentAndStillRevokesAnyTokenLeft() {
        registerUser("maria", Role.OPERATOR);
        service.deactivate(deactivation("maria", SOMEONE_ELSE));
        var savesBefore = repository.saves();

        service.deactivate(deactivation("maria", SOMEONE_ELSE));

        assertThat(repository.saves()).isEqualTo(savesBefore);
        assertThat(stored("maria").isActive()).isFalse();
        assertThat(revokedFor).hasSize(2);
    }

    @Test
    void shouldRefuseToDeactivateTheUserWhoIsDoingIt() {
        registerUser("chefe", Role.ADMIN);
        registerUser("vice", Role.ADMIN);
        var chefe = stored("chefe").getId();

        assertThatThrownBy(() -> service.deactivate(new DeactivateUserCommand(chefe, chefe)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("próprio usuário");

        assertThat(stored("chefe").isActive()).isTrue();
        assertThat(revokedFor).isEmpty();
    }

    @Test
    void shouldRefuseToDeactivateTheLastActiveAdministrator() {
        registerUser("chefe", Role.ADMIN);
        registerUser("maria", Role.OPERATOR);

        assertThatThrownBy(() -> service.deactivate(deactivation("chefe", SOMEONE_ELSE)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("último administrador");

        assertThat(stored("chefe").isActive()).isTrue();
        assertThat(revokedFor).isEmpty();
    }

    @Test
    void shouldDeactivateAnAdministratorWhenAnotherActiveOneRemains() {
        registerUser("chefe", Role.ADMIN);
        registerUser("vice", Role.ADMIN);

        service.deactivate(deactivation("chefe", stored("vice").getId()));

        assertThat(stored("chefe").isActive()).isFalse();
        assertThat(stored("vice").isActive()).isTrue();
    }

    @Test
    void whenDeactivatingAnInactiveAdministratorDoesNotCountAsTheOneWhoRemains() {
        registerUser("chefe", Role.ADMIN);
        registerUser("antigo", Role.ADMIN);
        deactivate("antigo");

        assertThatThrownBy(() -> service.deactivate(deactivation("chefe", SOMEONE_ELSE)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void whenDeactivatingABlockedAdministratorStillCountsSoTheLockoutDoesNotStopTheRule() {
        registerUser("chefe", Role.ADMIN);
        registerUser("vice", Role.ADMIN);
        wrongPassword("vice", 3);

        service.deactivate(deactivation("chefe", SOMEONE_ELSE));

        assertThat(stored("chefe").isActive()).isFalse();
    }

    @Test
    void shouldDeactivateABlockedUserToo() {
        registerUser("maria", Role.OPERATOR);
        wrongPassword("maria", 3);

        service.deactivate(deactivation("maria", SOMEONE_ELSE));

        assertThat(stored("maria").isActive()).isFalse();
    }

    @Test
    void shouldRejectTheDeactivationOfAnUnknownUserAndRevokeNothing() {
        assertThatThrownBy(() -> service.deactivate(new DeactivateUserCommand(99_999L, SOMEONE_ELSE)))
                .isInstanceOf(com.riverfount.booksmanager.shared.domain.ResourceNotFoundException.class)
                .hasMessageContaining("99999");

        assertThat(revokedFor).isEmpty();
    }

    @Test
    void deactivatingAnOperatorNeverTouchesTheAdministrators() {
        registerUser("chefe", Role.ADMIN);
        registerUser("maria", Role.OPERATOR);

        service.deactivate(deactivation("maria", stored("chefe").getId()));

        assertThat(stored("chefe").isActive()).isTrue();
        assertThat(stored("chefe").getRole()).isEqualTo(Role.ADMIN);
    }

    // resetting a password (RF09, HU29, RNF07)

    private static final String NEW_PASSWORD = "N3w-Pass#word";

    private ResetUserPasswordCommand reset(String login, String password) {
        return new ResetUserPasswordCommand(stored(login).getId(), password);
    }

    @Test
    void shouldResetThePasswordStoringOnlyTheHashAndRevokeTheRefreshTokens() {
        registerUser("maria", Role.OPERATOR);
        var oldHash = stored("maria").getPasswordHash();

        service.resetPassword(reset("maria", NEW_PASSWORD));

        var after = stored("maria");
        assertThat(after.getPasswordHash()).isNotEqualTo(oldHash).doesNotContain(NEW_PASSWORD);
        assertThat(hasher.matches(NEW_PASSWORD, after.getPasswordHash())).isTrue();
        assertThat(revokedFor).containsExactly(after.getId());
    }

    @Test
    void theOldPasswordStopsWorkingAndTheNewOneLogsIn() {
        registerUser("maria", Role.OPERATOR);

        service.resetPassword(reset("maria", NEW_PASSWORD));

        assertThat(canLogIn("maria")).isFalse();
        assertThat(service.authenticate(new AuthenticateUserCommand("maria", NEW_PASSWORD)).role())
                .isEqualTo(Role.OPERATOR);
    }

    @Test
    void resettingThePasswordShouldChangeNothingElseInTheUser() {
        registerUser("chefe", Role.ADMIN);
        var before = stored("chefe");

        service.resetPassword(reset("chefe", NEW_PASSWORD));

        var after = stored("chefe");
        assertThat(after.getLogin()).isEqualTo(before.getLogin());
        assertThat(after.getRole()).isEqualTo(Role.ADMIN);
        assertThat(after.isActive()).isTrue();
    }

    @Test
    void shouldRefuseAWeakPasswordKeepingTheCurrentOneAndTheTokens() {
        registerUser("maria", Role.OPERATOR);
        var oldHash = stored("maria").getPasswordHash();

        for (var weak : new String[] {"Sh0rt!", "alllowercase1!", "ALLUPPERCASE1!", "NoDigitsHere!", "NoSpecial1234",
                "Aa1!" + "x".repeat(30)}) {
            assertThatThrownBy(() -> service.resetPassword(reset("maria", weak)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("senha")
                    .hasMessageNotContaining(weak);
        }

        assertThat(stored("maria").getPasswordHash()).isEqualTo(oldHash);
        assertThat(revokedFor).isEmpty();
    }

    @Test
    void shouldRejectTheResetOfAnUnknownUserWithoutRevokingAnything() {
        assertThatThrownBy(() -> service.resetPassword(new ResetUserPasswordCommand(99_999L, NEW_PASSWORD)))
                .isInstanceOf(com.riverfount.booksmanager.shared.domain.ResourceNotFoundException.class);

        assertThat(revokedFor).isEmpty();
    }

    @Test
    void shouldResetThePasswordOfAnInactiveUserWithoutReactivatingIt() {
        registerUser("maria", Role.OPERATOR);
        deactivate("maria");

        service.resetPassword(reset("maria", NEW_PASSWORD));

        assertThat(stored("maria").isActive()).isFalse();
        assertThat(hasher.matches(NEW_PASSWORD, stored("maria").getPasswordHash())).isTrue();
        assertThat(revokedFor).containsExactly(stored("maria").getId());
    }

    @Test
    void theResetCommandShouldNotShowThePasswordInItsStringForm() {
        assertThat(new ResetUserPasswordCommand(5L, NEW_PASSWORD).toString())
                .doesNotContain(NEW_PASSWORD).contains("5");
    }
}
