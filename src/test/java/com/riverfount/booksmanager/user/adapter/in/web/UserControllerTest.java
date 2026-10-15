package com.riverfount.booksmanager.user.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.riverfount.booksmanager.WithAdmin;
import com.riverfount.booksmanager.WithOperator;
import com.riverfount.booksmanager.security.configuration.MethodSecurityConfiguration;
import com.riverfount.booksmanager.shared.adapter.in.web.GlobalExceptionHandler;
import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import com.riverfount.booksmanager.shared.domain.PageResult;
import com.riverfount.booksmanager.shared.domain.ResourceNotFoundException;
import com.riverfount.booksmanager.shared.domain.SortDirection;
import com.riverfount.booksmanager.user.application.port.in.CreateUserCommand;
import com.riverfount.booksmanager.user.application.port.in.CreateUserUseCase;
import com.riverfount.booksmanager.user.application.port.in.GetUserUseCase;
import com.riverfount.booksmanager.user.application.port.in.ListUsersQuery;
import com.riverfount.booksmanager.user.application.port.in.ListUsersUseCase;
import com.riverfount.booksmanager.user.application.port.in.UnblockUserUseCase;
import com.riverfount.booksmanager.user.application.port.in.UpdateUserCommand;
import com.riverfount.booksmanager.user.application.port.in.UpdateUserUseCase;
import com.riverfount.booksmanager.user.domain.Role;
import com.riverfount.booksmanager.user.domain.User;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(UserController.class)
@Import({GlobalExceptionHandler.class, MethodSecurityConfiguration.class, UserControllerTest.FixedClock.class})
class UserControllerTest {

    private static final Instant NOW = Instant.parse("2026-10-15T12:00:00Z");

    /** A clock that stands still, so "blocked" is decided against a known instant. */
    @TestConfiguration
    static class FixedClock {
        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    private static final String BODY = "{\"login\":\"maria\",\"password\":\"N3w-Pass#word\",\"role\":\"OPERATOR\"}";
    private static final String HASH = "$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5BzqhZ8H9V5xQe4Fq0sDq6Z0yR3Wm";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CreateUserUseCase createUserUseCase;

    @MockitoBean
    private GetUserUseCase getUserUseCase;

    @MockitoBean
    private ListUsersUseCase listUsersUseCase;

    @MockitoBean
    private UpdateUserUseCase updateUserUseCase;

    @MockitoBean
    private UnblockUserUseCase unblockUserUseCase;

    private MockHttpServletRequestBuilder create(String json) {
        return post("/api/v1/users").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json);
    }

    private void givenTheUserIsCreated() {
        given(createUserUseCase.create(any())).willReturn(User.reconstitute(5L, "maria", HASH, Role.OPERATOR, true));
    }

    @Test
    @WithAdmin
    void shouldCreateAndReturn201WithLocationAndTheUserWithoutAnySecret() throws Exception {
        givenTheUserIsCreated();

        var response = mockMvc.perform(create(BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/users/5")))
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.login").value("maria"))
                .andExpect(jsonPath("$.role").value("OPERATOR"))
                .andExpect(jsonPath("$.active").value(true))
                .andReturn().getResponse();

        assertThat(response.getContentAsString()).doesNotContain("password").doesNotContain("Password")
                .doesNotContain("hash").doesNotContain(HASH).doesNotContain("N3w-Pass#word");
    }

    @Test
    @WithAdmin
    void shouldHandTheRequestToTheUseCaseAsACommand() throws Exception {
        givenTheUserIsCreated();

        mockMvc.perform(create(BODY)).andExpect(status().isCreated());

        var captor = ArgumentCaptor.forClass(CreateUserCommand.class);
        then(createUserUseCase).should().create(captor.capture());
        assertThat(captor.getValue()).isEqualTo(new CreateUserCommand("maria", "N3w-Pass#word", Role.OPERATOR));
    }

    @Test
    @WithAdmin
    void shouldAcceptTheAdministratorProfile() throws Exception {
        given(createUserUseCase.create(any())).willReturn(User.reconstitute(6L, "chefe", HASH, Role.ADMIN, true));

        mockMvc.perform(create("{\"login\":\"chefe\",\"password\":\"N3w-Pass#word\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    @WithAdmin
    void shouldReturn400AndNeverCallTheUseCaseForAMissingOrBlankField() throws Exception {
        for (var json : new String[] {"{}", "{\"password\":\"N3w-Pass#word\",\"role\":\"OPERATOR\"}",
                "{\"login\":\"maria\",\"role\":\"OPERATOR\"}", "{\"login\":\"maria\",\"password\":\"N3w-Pass#word\"}",
                "{\"login\":\"  \",\"password\":\"N3w-Pass#word\",\"role\":\"OPERATOR\"}",
                "{\"login\":\"maria\",\"password\":\"  \",\"role\":\"OPERATOR\"}"}) {
            mockMvc.perform(create(json))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        }

        then(createUserUseCase).should(never()).create(any());
    }

    @Test
    @WithAdmin
    void shouldReturn400ForAnUnknownProfileOrAMalformedBody() throws Exception {
        mockMvc.perform(create("{\"login\":\"maria\",\"password\":\"N3w-Pass#word\",\"role\":\"SUPERUSER\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(create("{\"login\":\"maria\",\"password\":\"N3w-Pass#word\",\"role\":\"operator\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(create("not json")).andExpect(status().isBadRequest());

        then(createUserUseCase).should(never()).create(any());
    }

    @Test
    @WithAdmin
    void shouldReturn400ForAnOversizedLoginOrPassword() throws Exception {
        mockMvc.perform(create("{\"login\":\"" + "a".repeat(51)
                + "\",\"password\":\"N3w-Pass#word\",\"role\":\"OPERATOR\"}")).andExpect(status().isBadRequest());
        mockMvc.perform(create("{\"login\":\"maria\",\"password\":\"" + "a".repeat(129)
                + "\",\"role\":\"OPERATOR\"}")).andExpect(status().isBadRequest());

        then(createUserUseCase).should(never()).create(any());
    }

    @Test
    @WithAdmin
    void shouldReturn422WithProblemDetailForARepeatedLogin() throws Exception {
        given(createUserUseCase.create(any()))
                .willThrow(new BusinessRuleException("já existe um usuário com o login 'maria'"));

        mockMvc.perform(create(BODY))
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.detail").value("já existe um usuário com o login 'maria'"));
    }

    @Test
    @WithAdmin
    void shouldReturn422NamingTheMissingRulesOfAWeakPassword() throws Exception {
        given(createUserUseCase.create(any())).willThrow(
                new BusinessRuleException("a senha não atende às regras: deve ter ao menos um número"));

        mockMvc.perform(create(BODY))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("a senha não atende às regras")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("N3w-Pass#word"))));
    }

    // only an administrator (RNF08)

    @Test
    @WithOperator
    void anOperatorGets403AndNothingIsCreated() throws Exception {
        mockMvc.perform(create(BODY))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.detail").value("Você não tem permissão para acessar este recurso."));

        then(createUserUseCase).should(never()).create(any());
    }

    @Test
    @WithMockUser(roles = "USER")
    void aUserWithAnotherProfileGets403() throws Exception {
        mockMvc.perform(create(BODY)).andExpect(status().isForbidden());

        then(createUserUseCase).should(never()).create(any());
    }

    @Test
    @WithMockUser(roles = {})
    void aUserWithNoProfileGets403() throws Exception {
        mockMvc.perform(create(BODY)).andExpect(status().isForbidden());

        then(createUserUseCase).should(never()).create(any());
    }

    // creation also shows the lockout state (always "not blocked" for a new user)

    @Test
    @WithAdmin
    void aCreatedUserIsShownAsNotBlocked() throws Exception {
        givenTheUserIsCreated();

        mockMvc.perform(create(BODY))
                .andExpect(jsonPath("$.blocked").value(false))
                .andExpect(jsonPath("$.blockedUntil").doesNotExist());
    }

    // getting a user (RF09, HU28)

    private static User blockedUntil(Instant until) {
        return User.reconstitute(8L, "bloqueada", HASH, Role.OPERATOR, true, 3, until);
    }

    @Test
    @WithAdmin
    void shouldGetAUserWithTheProfileAndTheState() throws Exception {
        given(getUserUseCase.findById(5L)).willReturn(
                Optional.of(User.reconstitute(5L, "maria", HASH, Role.OPERATOR, true)));

        var body = mockMvc.perform(get("/api/v1/users/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.login").value("maria"))
                .andExpect(jsonPath("$.role").value("OPERATOR"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.blocked").value(false))
                .andExpect(jsonPath("$.blockedUntil").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(HASH).doesNotContain("assword").doesNotContain("hash")
                .doesNotContain("failedLoginAttempts");
    }

    @Test
    @WithAdmin
    void shouldShowAnInactiveUserWithActiveFalse() throws Exception {
        given(getUserUseCase.findById(5L)).willReturn(
                Optional.of(User.reconstitute(5L, "maria", HASH, Role.OPERATOR, false)));

        mockMvc.perform(get("/api/v1/users/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    @WithAdmin
    void shouldShowAUserBlockedByTheLoginLockoutAndUntilWhen() throws Exception {
        given(getUserUseCase.findById(8L)).willReturn(Optional.of(blockedUntil(NOW.plusSeconds(600))));

        mockMvc.perform(get("/api/v1/users/8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blocked").value(true))
                .andExpect(jsonPath("$.blockedUntil").value("2026-10-15T12:10:00Z"));
    }

    @Test
    @WithAdmin
    void aBlockThatAlreadyEndedIsShownAsNotBlockedAndWithoutADate() throws Exception {
        given(getUserUseCase.findById(8L)).willReturn(Optional.of(blockedUntil(NOW.minusSeconds(1))));
        given(getUserUseCase.findById(9L)).willReturn(Optional.of(blockedUntil(NOW)));

        for (var id : new String[] {"8", "9"}) {
            mockMvc.perform(get("/api/v1/users/" + id))
                    .andExpect(jsonPath("$.blocked").value(false))
                    .andExpect(jsonPath("$.blockedUntil").doesNotExist());
        }
    }

    @Test
    @WithAdmin
    void shouldReturn404WithProblemDetailForAnUnknownUser() throws Exception {
        given(getUserUseCase.findById(99L)).willReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/users/99"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.detail").value("usuário não encontrado: 99"));
    }

    @Test
    @WithAdmin
    void shouldReturn400ForANonNumericId() throws Exception {
        mockMvc.perform(get("/api/v1/users/abc")).andExpect(status().isBadRequest());
    }

    // listing users (RF09, HU28, RNF14)

    private ListUsersQuery capturedQuery() {
        var captor = ArgumentCaptor.forClass(ListUsersQuery.class);
        then(listUsersUseCase).should().list(captor.capture());
        return captor.getValue();
    }

    @Test
    @WithAdmin
    void shouldListUsersWithThePageMetadataAndNoSecret() throws Exception {
        var maria = User.reconstitute(5L, "maria", HASH, Role.OPERATOR, true);
        given(listUsersUseCase.list(any())).willReturn(new PageResult<>(List.of(maria, blockedUntil(NOW.plusSeconds(60))), 0, 20, 2));

        var body = mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].login").value("maria"))
                .andExpect(jsonPath("$.items[0].blocked").value(false))
                .andExpect(jsonPath("$.items[1].login").value("bloqueada"))
                .andExpect(jsonPath("$.items[1].blocked").value(true))
                .andExpect(jsonPath("$.items[1].blockedUntil").value("2026-10-15T12:01:00Z"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(HASH).doesNotContain("assword").doesNotContain("hash");
    }

    @Test
    @WithAdmin
    void shouldListOnlyActiveUsersSortedByLoginByDefault() throws Exception {
        given(listUsersUseCase.list(any())).willReturn(new PageResult<>(List.of(), 0, 20, 0));

        mockMvc.perform(get("/api/v1/users")).andExpect(status().isOk());

        var query = capturedQuery();
        assertThat(query.active()).isTrue();
        assertThat(query.login()).isNull();
        assertThat(query.page().page()).isZero();
        assertThat(query.page().size()).isEqualTo(20);
        assertThat(query.page().sortBy()).isEqualTo("login");
        assertThat(query.page().direction()).isEqualTo(SortDirection.ASC);
    }

    @Test
    @WithAdmin
    void shouldPassTheFiltersPaginationAndSortToTheUseCase() throws Exception {
        given(listUsersUseCase.list(any())).willReturn(new PageResult<>(List.of(), 1, 5, 0));

        mockMvc.perform(get("/api/v1/users").param("login", "mar").param("active", "false")
                        .param("page", "1").param("size", "5").param("sort", "id,desc"))
                .andExpect(status().isOk());

        var query = capturedQuery();
        assertThat(query.login()).isEqualTo("mar");
        assertThat(query.active()).isFalse();
        assertThat(query.page().page()).isEqualTo(1);
        assertThat(query.page().size()).isEqualTo(5);
        assertThat(query.page().sortBy()).isEqualTo("id");
        assertThat(query.page().direction()).isEqualTo(SortDirection.DESC);
    }

    @Test
    @WithAdmin
    void shouldReturn400ForASizeAbove100AndForInvalidPaginationSortOrFilter() throws Exception {
        for (var param : new String[][] {{"size", "101"}, {"size", "0"}, {"page", "-1"}, {"sort", "role"},
                {"sort", "passwordHash,asc"}, {"sort", "login,sideways"}, {"active", "maybe"}}) {
            mockMvc.perform(get("/api/v1/users").param(param[0], param[1]))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        }

        then(listUsersUseCase).should(never()).list(any());
    }

    // only an administrator (RNF08)

    @Test
    @WithOperator
    void anOperatorGets403OnTheGetAndTheListRoutes() throws Exception {
        mockMvc.perform(get("/api/v1/users/5")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/users")).andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));

        then(getUserUseCase).should(never()).findById(any());
        then(listUsersUseCase).should(never()).list(any());
    }

    @Test
    @WithMockUser(roles = {})
    void aUserWithNoProfileGets403OnTheGetAndTheListRoutes() throws Exception {
        mockMvc.perform(get("/api/v1/users/5")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/users")).andExpect(status().isForbidden());
    }

    // editing the profile (RF09, HU28)

    private MockHttpServletRequestBuilder edit(long id, String json) {
        return patch("/api/v1/users/" + id).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json);
    }

    @Test
    @WithAdmin
    void shouldEditTheProfileAndReturn200WithTheUser() throws Exception {
        given(updateUserUseCase.update(any())).willReturn(User.reconstitute(5L, "maria", HASH, Role.ADMIN, true));

        var body = mockMvc.perform(edit(5, "{\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.login").value("maria"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.blocked").value(false))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(HASH).doesNotContain("assword").doesNotContain("hash");
        var captor = ArgumentCaptor.forClass(UpdateUserCommand.class);
        then(updateUserUseCase).should().update(captor.capture());
        assertThat(captor.getValue()).isEqualTo(new UpdateUserCommand(5L, Role.ADMIN));
    }

    @Test
    @WithAdmin
    void anEmptyBodyOrANullProfileKeepsTheProfile() throws Exception {
        given(updateUserUseCase.update(any())).willReturn(User.reconstitute(5L, "maria", HASH, Role.OPERATOR, true));

        mockMvc.perform(edit(5, "{}")).andExpect(status().isOk());
        mockMvc.perform(edit(5, "{\"role\":null}")).andExpect(status().isOk());

        var captor = ArgumentCaptor.forClass(UpdateUserCommand.class);
        then(updateUserUseCase).should(org.mockito.Mockito.times(2)).update(captor.capture());
        assertThat(captor.getAllValues()).containsOnly(new UpdateUserCommand(5L, null));
    }

    @Test
    @WithAdmin
    void shouldIgnoreTheFieldsThatThisRouteDoesNotChange() throws Exception {
        given(updateUserUseCase.update(any())).willReturn(User.reconstitute(5L, "maria", HASH, Role.OPERATOR, true));

        mockMvc.perform(edit(5, "{\"login\":\"intruso\",\"password\":\"N3w-Pass#word\",\"active\":false,"
                + "\"blocked\":true,\"id\":99,\"passwordHash\":\"x\"}")).andExpect(status().isOk());

        var captor = ArgumentCaptor.forClass(UpdateUserCommand.class);
        then(updateUserUseCase).should().update(captor.capture());
        assertThat(captor.getValue()).isEqualTo(new UpdateUserCommand(5L, null));
    }

    @Test
    @WithAdmin
    void shouldReturn400ForAnUnknownProfileOrAMalformedBodyOrANonNumericId() throws Exception {
        mockMvc.perform(edit(5, "{\"role\":\"SUPERUSER\"}")).andExpect(status().isBadRequest());
        mockMvc.perform(edit(5, "{\"role\":\"admin\"}")).andExpect(status().isBadRequest());
        mockMvc.perform(edit(5, "not json")).andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/v1/users/abc").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());

        then(updateUserUseCase).should(never()).update(any());
    }

    @Test
    @WithAdmin
    void shouldReturn404ForAnUnknownUserOnTheEdit() throws Exception {
        given(updateUserUseCase.update(any())).willThrow(new ResourceNotFoundException("usuário não encontrado: 99"));

        mockMvc.perform(edit(99, "{\"role\":\"ADMIN\"}"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.detail").value("usuário não encontrado: 99"));
    }

    @Test
    @WithAdmin
    void shouldReturn422WhenTheLastAdministratorWouldLoseTheProfile() throws Exception {
        given(updateUserUseCase.update(any())).willThrow(new BusinessRuleException(
                "não é possível tirar o perfil de administrador do último administrador ativo"));

        mockMvc.perform(edit(1, "{\"role\":\"OPERATOR\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.detail").value(containsString("último administrador")));
    }

    // unblocking (RF08, RF09)

    private MockHttpServletRequestBuilder unblock(String id) {
        return post("/api/v1/users/" + id + "/unblock").with(csrf());
    }

    @Test
    @WithAdmin
    void shouldUnblockAndReturn204WithoutBody() throws Exception {
        mockMvc.perform(unblock("5")).andExpect(status().isNoContent()).andExpect(content().string(""));

        then(unblockUserUseCase).should().unblock(5L);
    }

    @Test
    @WithAdmin
    void unblockingTwiceKeepsReturning204() throws Exception {
        mockMvc.perform(unblock("5")).andExpect(status().isNoContent());
        mockMvc.perform(unblock("5")).andExpect(status().isNoContent());
    }

    @Test
    @WithAdmin
    void shouldReturn404WhenUnblockingAnUnknownUserAnd400ForANonNumericId() throws Exception {
        willThrow(new ResourceNotFoundException("usuário não encontrado: 99")).given(unblockUserUseCase).unblock(99L);

        mockMvc.perform(unblock("99")).andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        mockMvc.perform(unblock("abc")).andExpect(status().isBadRequest());
    }

    // only an administrator (RNF08)

    @Test
    @WithOperator
    void anOperatorGets403OnTheEditAndTheUnblockAndNothingChanges() throws Exception {
        mockMvc.perform(edit(5, "{\"role\":\"ADMIN\"}")).andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        mockMvc.perform(unblock("5")).andExpect(status().isForbidden());

        then(updateUserUseCase).should(never()).update(any());
        then(unblockUserUseCase).should(never()).unblock(any());
    }

    @Test
    @WithMockUser(roles = {})
    void aUserWithNoProfileGets403OnTheEditAndTheUnblock() throws Exception {
        mockMvc.perform(edit(5, "{\"role\":\"ADMIN\"}")).andExpect(status().isForbidden());
        mockMvc.perform(unblock("5")).andExpect(status().isForbidden());
    }
}
