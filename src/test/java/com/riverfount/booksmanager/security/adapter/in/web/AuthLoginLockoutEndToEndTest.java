package com.riverfount.booksmanager.security.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.riverfount.booksmanager.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * The login lockout (RF08) through the HTTP route, with no test transaction, so
 * what the application commits is what is read back. A test transaction would
 * hide the case this one exists for: the login service is itself transactional,
 * and a refused login throws; if that exception rolled the whole transaction
 * back, the wrong password would never be recorded and the user would never be
 * blocked, which only shows with real commits.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class AuthLoginLockoutEndToEndTest {

    private static final String LOGIN = "lockout-e2e";
    private static final String PASSWORD = "S3cret-Pass!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void createTheUser() {
        cleanUp();
        jdbcTemplate.update("insert into app_user (login, password_hash, role) values (?, ?, 'OPERATOR')",
                LOGIN, new BCryptPasswordEncoder().encode(PASSWORD));
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from refresh_token where user_id in (select id from app_user where login = ?)",
                LOGIN);
        jdbcTemplate.update("delete from app_user where login = ?", LOGIN);
    }

    private ResultActions login(String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"" + LOGIN + "\",\"password\":\"" + password + "\"}"));
    }

    private int failedAttempts() {
        return jdbcTemplate.queryForObject("select failed_login_attempts from app_user where login = ?",
                Integer.class, LOGIN);
    }

    @Test
    void aWrongPasswordShouldBeRecordedEvenThoughTheLoginRequestEndsInAnError() throws Exception {
        login("Wrong-Pass1!").andExpect(status().isUnauthorized());
        login("Wrong-Pass1!").andExpect(status().isUnauthorized());

        assertThat(failedAttempts()).isEqualTo(2);
    }

    @Test
    void threeWrongPasswordsShouldBlockTheUserSoEvenTheRightPasswordIsRefused() throws Exception {
        for (var i = 0; i < 3; i++) {
            login("Wrong-Pass1!").andExpect(status().isUnauthorized());
        }

        var refused = login(PASSWORD).andExpect(status().isUnauthorized()).andReturn().getResponse();

        assertThat(refused.getHeaders("Set-Cookie")).isEmpty();
        assertThat(jdbcTemplate.queryForObject("select blocked_until is not null from app_user where login = ?",
                Boolean.class, LOGIN)).isTrue();
        assertThat(jdbcTemplate.queryForObject("select count(*) from refresh_token", Integer.class)).isZero();
    }

    @Test
    void aSuccessfulLoginShouldResetTheCountForReal() throws Exception {
        login("Wrong-Pass1!").andExpect(status().isUnauthorized());
        login("Wrong-Pass1!").andExpect(status().isUnauthorized());

        login(PASSWORD).andExpect(status().isOk());

        assertThat(failedAttempts()).isZero();
    }
}
