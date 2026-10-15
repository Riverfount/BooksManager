package com.riverfount.booksmanager.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import com.riverfount.booksmanager.shared.domain.InvalidCredentialsException;
import com.riverfount.booksmanager.user.application.port.in.AuthenticateUserCommand;
import com.riverfount.booksmanager.user.application.port.in.AuthenticatedUser;
import com.riverfount.booksmanager.user.application.port.in.CreateInitialAdminCommand;
import com.riverfount.booksmanager.user.application.port.in.InitialAdminResult;
import com.riverfount.booksmanager.user.application.port.out.PasswordHasher;
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

    private InMemoryUserRepository repository;
    private FakePasswordHasher hasher;
    private UserService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryUserRepository();
        hasher = new FakePasswordHasher();
        service = new UserService(repository, hasher);
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
}
