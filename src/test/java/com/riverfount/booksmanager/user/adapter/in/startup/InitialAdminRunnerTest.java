package com.riverfount.booksmanager.user.adapter.in.startup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.TestcontainersConfiguration;
import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import com.riverfount.booksmanager.user.application.port.in.CreateInitialAdminUseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

/**
 * The initial administrator (RF08, RNF10) with the real service, adapter and
 * PostgreSQL. Each test runs in a transaction that is rolled back, and builds
 * the runner by hand with the properties it needs, so the shared database
 * is left untouched and the application itself starts without the variables.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
@ExtendWith(OutputCaptureExtension.class)
class InitialAdminRunnerTest {

    private static final String PASSWORD = "S3cret-Pass-For-Test!";

    @Autowired
    private CreateInitialAdminUseCase useCase;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private InitialAdminRunner runner(String login, String password) {
        return new InitialAdminRunner(useCase, new AdminProperties(login, password));
    }

    private int countUsers() {
        return jdbcTemplate.queryForObject("select count(*) from app_user", Integer.class);
    }

    @Test
    void theApplicationShouldStartWithoutTheVariablesAndWithoutCreatingAnyUser() {
        assertThat(countUsers()).isZero();
    }

    @Test
    void shouldCreateTheAdministratorWithABcryptPasswordWhenTheTableIsEmpty(CapturedOutput output) throws Exception {
        runner("Admin", PASSWORD).run(null);

        var row = jdbcTemplate.queryForMap("select login, password_hash, role, active from app_user");
        assertThat(row.get("login")).isEqualTo("admin");
        assertThat(row.get("role")).isEqualTo("ADMIN");
        assertThat(row.get("active")).isEqualTo(true);
        var hash = (String) row.get("password_hash");
        assertThat(hash).startsWith("$2").isNotEqualTo(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, hash)).isTrue();
        assertThat(output.getAll()).doesNotContain(PASSWORD).doesNotContain(hash);
    }

    @Test
    void shouldNotCreateAnythingWhenThereIsAlreadyAUser() throws Exception {
        jdbcTemplate.update("insert into app_user (login, password_hash, role) values ('operator', 'x', 'OPERATOR')");

        runner("admin", PASSWORD).run(null);

        assertThat(countUsers()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select count(*) from app_user where login = 'admin'",
                Integer.class)).isZero();
    }

    @Test
    void restartingShouldNeitherDuplicateNorChangeTheAdministrator() throws Exception {
        runner("admin", PASSWORD).run(null);
        var before = jdbcTemplate.queryForMap("select id, password_hash from app_user");

        runner("admin", PASSWORD).run(null);
        runner("admin", "Different-Pass9!").run(null);

        assertThat(countUsers()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForMap("select id, password_hash from app_user")).isEqualTo(before);
    }

    @Test
    void shouldFailTheStartupWithAClearMessageWhenThePasswordIsWeak(CapturedOutput output) {
        var weakRunner = runner("admin", "weakpass");

        assertThatThrownBy(() -> weakRunner.run(null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("senha")
                .hasMessageNotContaining("weakpass");

        assertThat(countUsers()).isZero();
        assertThat(output.getAll()).doesNotContain("weakpass");
    }

    @Test
    void shouldNotCreateWithoutTheVariablesAndWarnWithoutShowingAnySecret(CapturedOutput output) throws Exception {
        runner("", "").run(null);
        runner("admin", "").run(null);
        runner(null, PASSWORD).run(null);

        assertThat(countUsers()).isZero();
        assertThat(output.getAll()).contains("APP_ADMIN_LOGIN").contains("APP_ADMIN_PASSWORD")
                .doesNotContain(PASSWORD);
    }
}
