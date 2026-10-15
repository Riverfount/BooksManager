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
import com.riverfount.booksmanager.security.application.port.in.TokenPair;
import com.riverfount.booksmanager.security.application.port.in.InvalidRefreshTokenException;
import com.riverfount.booksmanager.security.application.port.in.LoginUseCase;
import com.riverfount.booksmanager.security.application.port.in.LogoutCommand;
import com.riverfount.booksmanager.security.application.port.in.LogoutUseCase;
import com.riverfount.booksmanager.security.application.port.in.RefreshCommand;
import com.riverfount.booksmanager.security.application.port.in.RefreshTokenUseCase;
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
import jakarta.servlet.http.Cookie;

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

    @MockitoBean
    private RefreshTokenUseCase refreshTokenUseCase;

    @MockitoBean
    private LogoutUseCase logoutUseCase;

    private MockHttpServletRequestBuilder login(String json) {
        return post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json);
    }

    private void givenALoginThatSucceeds() {
        given(loginUseCase.login(any())).willReturn(
                new TokenPair("the-access-token", 900, "the-refresh-token", Duration.ofDays(7)));
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

    // renewing the token (RF08)

    private static final String CSRF_HEADER = "X-Requested-With";
    private static final String CSRF_VALUE = "XMLHttpRequest";

    private MockHttpServletRequestBuilder refresh(String cookieValue) {
        var request = post("/api/v1/auth/refresh").with(csrf()).header(CSRF_HEADER, CSRF_VALUE);
        return cookieValue == null ? request : request.cookie(new Cookie("refresh_token", cookieValue));
    }

    private MockHttpServletRequestBuilder logout(String cookieValue) {
        var request = post("/api/v1/auth/logout").with(csrf()).header(CSRF_HEADER, CSRF_VALUE);
        return cookieValue == null ? request : request.cookie(new Cookie("refresh_token", cookieValue));
    }

    private static void assertClearsTheCookie(String setCookie) {
        assertThat(setCookie).startsWith("refresh_token=;").contains("Max-Age=0").contains("HttpOnly")
                .contains("Secure").contains("SameSite=Strict").contains("Path=/api/v1/auth");
    }

    @Test
    @WithMockUser
    void refreshShouldGiveANewAccessTokenInTheBodyAndTheRotatedRefreshTokenInTheCookie() throws Exception {
        given(refreshTokenUseCase.refresh(any())).willReturn(
                new TokenPair("new-access-token", 900, "new-refresh-token", Duration.ofDays(7)));

        var response = mockMvc.perform(refresh("old-refresh-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andReturn().getResponse();

        assertThat(response.getContentAsString()).doesNotContain("new-refresh-token");
        assertThat(response.getHeader("Set-Cookie")).startsWith("refresh_token=new-refresh-token;")
                .contains("HttpOnly").contains("Secure").contains("SameSite=Strict")
                .contains("Path=/api/v1/auth").contains("Max-Age=604800");
        assertThat(response.getHeaders("Set-Cookie")).hasSize(1);
    }

    @Test
    @WithMockUser
    void refreshShouldHandTheCookieValueToTheUseCase() throws Exception {
        given(refreshTokenUseCase.refresh(any())).willReturn(
                new TokenPair("a", 900, "b", Duration.ofDays(7)));

        mockMvc.perform(refresh("old-refresh-token")).andExpect(status().isOk());

        var captor = ArgumentCaptor.forClass(RefreshCommand.class);
        then(refreshTokenUseCase).should().refresh(captor.capture());
        assertThat(captor.getValue()).isEqualTo(new RefreshCommand("old-refresh-token"));
    }

    @Test
    @WithMockUser
    void refreshShouldIgnoreACookieWithAnotherName() throws Exception {
        given(refreshTokenUseCase.refresh(any())).willThrow(new InvalidRefreshTokenException());

        mockMvc.perform(post("/api/v1/auth/refresh").with(csrf()).header(CSRF_HEADER, CSRF_VALUE)
                        .cookie(new Cookie("other_cookie", "some-value")))
                .andExpect(status().isUnauthorized());

        var captor = ArgumentCaptor.forClass(RefreshCommand.class);
        then(refreshTokenUseCase).should().refresh(captor.capture());
        assertThat(captor.getValue().refreshToken()).isNull();
    }

    @Test
    @WithMockUser
    void aRefusedRefreshShouldBe401AsProblemDetailAndClearTheCookie() throws Exception {
        given(refreshTokenUseCase.refresh(any())).willThrow(new InvalidRefreshTokenException());

        var response = mockMvc.perform(refresh("stale-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value("Refresh token inválido, expirado ou revogado."))
                .andReturn().getResponse();

        assertClearsTheCookie(response.getHeader("Set-Cookie"));
        assertThat(response.getContentAsString()).doesNotContain("stale-token");
    }

    @Test
    @WithMockUser
    void refreshWithoutTheCsrfHeaderShouldBe403AndNeverReachTheUseCase() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh").with(csrf())
                        .cookie(new Cookie("refresh_token", "old-refresh-token")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("X-Requested-With")))
                .andExpect(header().doesNotExist("Set-Cookie"));

        then(refreshTokenUseCase).should(never()).refresh(any());
    }

    @Test
    @WithMockUser
    void refreshWithAWrongCsrfHeaderValueShouldBe403() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh").with(csrf()).header(CSRF_HEADER, "fetch")
                        .cookie(new Cookie("refresh_token", "old-refresh-token")))
                .andExpect(status().isForbidden());

        then(refreshTokenUseCase).should(never()).refresh(any());
    }

    @Test
    @WithMockUser
    void theCsrfHeaderValueShouldNotBeCaseSensitive() throws Exception {
        given(refreshTokenUseCase.refresh(any())).willReturn(new TokenPair("a", 900, "b", Duration.ofDays(7)));

        mockMvc.perform(post("/api/v1/auth/refresh").with(csrf()).header(CSRF_HEADER, "xmlhttprequest")
                        .cookie(new Cookie("refresh_token", "old-refresh-token")))
                .andExpect(status().isOk());
    }

    // logout (RF08)

    @Test
    @WithMockUser
    void logoutShouldRevokeTheCookieTokenClearTheCookieAndReturn204() throws Exception {
        var response = mockMvc.perform(logout("the-token")).andExpect(status().isNoContent())
                .andExpect(content().string("")).andReturn().getResponse();

        var captor = ArgumentCaptor.forClass(LogoutCommand.class);
        then(logoutUseCase).should().logout(captor.capture());
        assertThat(captor.getValue()).isEqualTo(new LogoutCommand("the-token"));
        assertClearsTheCookie(response.getHeader("Set-Cookie"));
    }

    @Test
    @WithMockUser
    void logoutWithoutACookieShouldStillBe204AndClearTheCookie() throws Exception {
        var response = mockMvc.perform(logout(null)).andExpect(status().isNoContent()).andReturn().getResponse();

        assertClearsTheCookie(response.getHeader("Set-Cookie"));
    }

    @Test
    @WithMockUser
    void logoutWithoutTheCsrfHeaderShouldBe403AndNeverReachTheUseCase() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout").with(csrf())
                        .cookie(new Cookie("refresh_token", "the-token")))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Set-Cookie"));

        then(logoutUseCase).should(never()).logout(any());
    }
}
