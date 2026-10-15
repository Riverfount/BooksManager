package com.riverfount.booksmanager.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
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

        @Override
        public String hash(String rawPassword) {
            return "{hash-" + (++calls) + "}" + new StringBuilder(rawPassword).reverse();
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
}
