package com.riverfount.booksmanager.security.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.riverfount.booksmanager.security.application.port.in.LoginCommand;
import com.riverfount.booksmanager.security.application.port.in.LoginResult;
import com.riverfount.booksmanager.security.application.port.in.LoginUseCase;
import com.riverfount.booksmanager.security.configuration.AuthProperties;
import com.riverfount.booksmanager.shared.adapter.in.web.GlobalExceptionHandler;
import com.riverfount.booksmanager.shared.domain.InvalidCredentialsException;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(AuthController.class)
@Import(GlobalExceptionHandler.class)
@EnableConfigurationProperties(AuthProperties.class)
@TestPropertySource(properties = {"app.auth.refresh-token-ttl=7d", "app.auth.refresh-cookie.name=refresh_token",
        "app.auth.refresh-cookie.secure=true", "app.auth.refresh-cookie.same-site=Strict",
        "app.auth.refresh-cookie.path=/api/v1/auth"})
class AuthControllerTest {

    private static final String BODY = "{\"login\":\"maria\",\"password\":\"S3cret-Pass!\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LoginUseCase loginUseCase;

    private MockHttpServletRequestBuilder login(String json) {
        return post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json);
    }

    private void givenALoginThatSucceeds() {
        given(loginUseCase.login(any())).willReturn(
                new LoginResult("the-access-token", 900, "the-refresh-token", Duration.ofDays(7)));
    }

    @Test
    @WithMockUser
    void shouldReturnTheAccessTokenInTheBodyWithoutTheRefreshToken() throws Exception {
        givenALoginThatSucceeds();

        mockMvc.perform(login(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("the-access-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("the-refresh-token"))));
    }

    @Test
    @WithMockUser
    void shouldSendTheRefreshTokenInAnHttpOnlyCookieWithTheConfiguredAttributes() throws Exception {
        givenALoginThatSucceeds();

        var cookie = mockMvc.perform(login(BODY)).andExpect(status().isOk())
                .andReturn().getResponse().getHeader("Set-Cookie");

        assertThat(cookie).startsWith("refresh_token=the-refresh-token;")
                .contains("HttpOnly").contains("Secure").contains("SameSite=Strict")
                .contains("Path=/api/v1/auth").contains("Max-Age=604800");
    }

    @Test
    @WithMockUser
    void shouldSendOneCookieOnly() throws Exception {
        givenALoginThatSucceeds();

        var cookies = mockMvc.perform(login(BODY)).andReturn().getResponse().getHeaders("Set-Cookie");

        assertThat(cookies).hasSize(1);
    }

    @Test
    @WithMockUser
    void shouldPassTheCredentialsToTheUseCaseAsACommand() throws Exception {
        givenALoginThatSucceeds();

        mockMvc.perform(login(BODY)).andExpect(status().isOk());

        var captor = ArgumentCaptor.forClass(LoginCommand.class);
        then(loginUseCase).should().login(captor.capture());
        assertThat(captor.getValue()).isEqualTo(new LoginCommand("maria", "S3cret-Pass!"));
    }

    @Test
    @WithMockUser
    void shouldReturn401AsProblemDetailWithAFixedMessageAndNoCookieWhenTheCredentialsAreRefused() throws Exception {
        given(loginUseCase.login(any())).willThrow(new InvalidCredentialsException());

        mockMvc.perform(login(BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value("Login ou senha inválidos."))
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test
    @WithMockUser
    void shouldReturn400AndNeverCallTheUseCaseWhenTheFieldsAreMissingOrBlank() throws Exception {
        for (var json : new String[] {"{}", "{\"login\":\"maria\"}", "{\"password\":\"S3cret-Pass!\"}",
                "{\"login\":\"\",\"password\":\"S3cret-Pass!\"}", "{\"login\":\"maria\",\"password\":\"  \"}"}) {
            mockMvc.perform(login(json))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                    .andExpect(header().doesNotExist("Set-Cookie"));
        }

        then(loginUseCase).should(never()).login(any());
    }

    @Test
    @WithMockUser
    void shouldReturn400ForAnOversizedLoginOrPasswordWithoutTouchingTheHasher() throws Exception {
        mockMvc.perform(login("{\"login\":\"" + "a".repeat(51) + "\",\"password\":\"S3cret-Pass!\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(login("{\"login\":\"maria\",\"password\":\"" + "a".repeat(129) + "\"}"))
                .andExpect(status().isBadRequest());

        then(loginUseCase).should(never()).login(any());
    }

    @Test
    @WithMockUser
    void shouldReturn400ForAMalformedBody() throws Exception {
        mockMvc.perform(login("not json")).andExpect(status().isBadRequest());
    }
}
