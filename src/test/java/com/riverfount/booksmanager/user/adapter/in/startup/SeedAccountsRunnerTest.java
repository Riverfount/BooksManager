package com.riverfount.booksmanager.user.adapter.in.startup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.TestcontainersConfiguration;
import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import com.riverfount.booksmanager.user.application.port.in.SeedAccountsUseCase;
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
import org.springframework.transaction.annotation.Transactional;

/**
 * The optional operator and reader created at startup, with the real service, adapters and PostgreSQL. Each test
 * runs in a transaction that is rolled back, and builds the runner by hand with the properties it needs.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
@ExtendWith(OutputCaptureExtension.class)
class SeedAccountsRunnerTest {

    private static final String PASSWORD = "S3cret-Pass-For-Test!";

    @Autowired
    private SeedAccountsUseCase useCase;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private SeedAccountsRunner runner(SeedAccountsProperties.Operator operator, SeedAccountsProperties.Reader reader) {
        return new SeedAccountsRunner(useCase, new SeedAccountsProperties(operator, reader));
    }

    private static SeedAccountsProperties.Operator operator(String login, String password) {
        return new SeedAccountsProperties.Operator(login, password);
    }

    private static SeedAccountsProperties.Reader reader(String login, String password, String birthDate) {
        return new SeedAccountsProperties.Reader(login, password, "Maria Leitora", "123.456.789-09",
                "maria@example.com", birthDate);
    }

    private static final SeedAccountsProperties.Reader NO_READER = reader(null, null, null);
    private static final SeedAccountsProperties.Operator NO_OPERATOR = operator(null, null);

    private int count(String sql) {
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }

    @Test
    void shouldCreateTheOperatorWithABcryptPassword(CapturedOutput output) {
        runner(operator("Operator", PASSWORD), NO_READER).run(null);

        var row = jdbcTemplate.queryForMap("select login, password_hash, role, reader_id from app_user");
        assertThat(row.get("login")).isEqualTo("operator");
        assertThat(row.get("role")).isEqualTo("OPERATOR");
        assertThat(row.get("reader_id")).isNull();
        var hash = (String) row.get("password_hash");
        assertThat(new BCryptPasswordEncoder().matches(PASSWORD, hash)).isTrue();
        assertThat(output.getAll()).doesNotContain(PASSWORD).doesNotContain(hash);
    }

    @Test
    void shouldCreateTheReaderTiedToItsReaderRecord() {
        runner(NO_OPERATOR, reader("leitora", PASSWORD, "1990-05-20")).run(null);

        var row = jdbcTemplate.queryForMap("select u.role, u.reader_id, r.name from app_user u "
                + "join reader r on r.id = u.reader_id where u.login = 'leitora'");
        assertThat(row.get("role")).isEqualTo("READER");
        assertThat(row.get("name")).isEqualTo("Maria Leitora");
        assertThat(count("select count(*) from reader")).isEqualTo(1);
    }

    @Test
    void shouldWorkOnADatabaseThatAlreadyHasUsersAndRestartingShouldChangeNothing() {
        jdbcTemplate.update("insert into app_user (login, password_hash, role) values ('admin', 'x', 'ADMIN')");
        var runner = runner(operator("operator", PASSWORD), reader("leitora", PASSWORD, "1990-05-20"));

        runner.run(null);
        var before = jdbcTemplate.queryForList("select id, password_hash from app_user order by id");
        runner.run(null);

        assertThat(count("select count(*) from app_user")).isEqualTo(3);
        assertThat(count("select count(*) from reader")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForList("select id, password_hash from app_user order by id")).isEqualTo(before);
    }

    @Test
    void shouldNotCreateAnythingWhenTheConfigurationIsIncompleteAndWarnWithoutSecrets(CapturedOutput output) {
        runner(operator("operator", ""), reader("leitora", PASSWORD, null)).run(null);

        assertThat(count("select count(*) from app_user")).isZero();
        assertThat(count("select count(*) from reader")).isZero();
        assertThat(output.getAll()).contains("APP_OPERATOR_PASSWORD").contains("APP_READER_BIRTH_DATE")
                .doesNotContain(PASSWORD);
    }

    @Test
    void shouldStaySilentAndCreateNothingWhenNothingIsConfigured(CapturedOutput output) {
        runner(NO_OPERATOR, NO_READER).run(null);

        assertThat(count("select count(*) from app_user")).isZero();
        assertThat(output.getAll()).doesNotContain("was not created");
    }

    @Test
    void shouldFailWithAClearMessageOnAWeakPasswordAndOnAMalformedBirthDate() {
        var weak = runner(operator("operator", "weakpass"), NO_READER);
        var badDate = runner(NO_OPERATOR, reader("leitora", PASSWORD, "20/05/1990"));

        assertThatThrownBy(() -> weak.run(null)).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("senha").hasMessageNotContaining("weakpass");
        assertThatThrownBy(() -> badDate.run(null)).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("AAAA-MM-DD");
        assertThat(count("select count(*) from app_user")).isZero();
    }

    @Test
    void shouldRefuseAReaderWhoseDocumentBelongsToAnotherReaderWithoutLeavingAUser() {
        jdbcTemplate.update("insert into app_user (login, password_hash, role) values ('admin', 'x', 'ADMIN')");
        runner(NO_OPERATOR, reader("leitora", PASSWORD, "1990-05-20")).run(null);
        var other = runner(NO_OPERATOR, reader("outra", PASSWORD, "1990-05-20"));

        assertThatThrownBy(() -> other.run(null)).isInstanceOf(BusinessRuleException.class);
        assertThat(count("select count(*) from app_user where login = 'outra'")).isZero();
    }

    @Test
    void shouldKeepThePasswordsOutOfTheStringForms() {
        var properties = new SeedAccountsProperties(operator("operator", PASSWORD), reader("leitora", PASSWORD, null));

        assertThat(properties.toString()).doesNotContain(PASSWORD);
    }
}
