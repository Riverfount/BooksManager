package com.riverfount.booksmanager.user.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.riverfount.booksmanager.WithAdmin;
import com.riverfount.booksmanager.WithOperator;
import com.riverfount.booksmanager.security.configuration.MethodSecurityConfiguration;
import com.riverfount.booksmanager.shared.adapter.in.web.GlobalExceptionHandler;
import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import com.riverfount.booksmanager.user.application.port.in.ChangeOwnPasswordCommand;
import com.riverfount.booksmanager.user.application.port.in.ChangeOwnPasswordUseCase;
import com.riverfount.booksmanager.user.application.port.in.GetOwnProfileUseCase;
import com.riverfount.booksmanager.user.application.port.in.OwnProfile;
import com.riverfount.booksmanager.user.application.port.in.UpdateOwnProfileCommand;
import com.riverfount.booksmanager.user.application.port.in.UpdateOwnProfileUseCase;
import com.riverfount.booksmanager.user.domain.Guardian;
import com.riverfount.booksmanager.user.domain.Reader;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * The reader maintains their own registration (RF32): the reader is the one of the token, never one sent in the
 * request; the answer has exactly the fields of the contract; no password comes back; staff profiles are kept out.
 */
@WebMvcTest(OwnProfileController.class)
@Import({GlobalExceptionHandler.class, MethodSecurityConfiguration.class})
class OwnProfileControllerTest {

    private static final long ME = 42L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetOwnProfileUseCase getUseCase;

    @MockitoBean
    private UpdateOwnProfileUseCase updateUseCase;

    @MockitoBean
    private ChangeOwnPasswordUseCase passwordUseCase;

    private static RequestPostProcessor reader() {
        return jwt().jwt(token -> token.subject("5").claim("readerId", ME))
                .authorities(new SimpleGrantedAuthority("ROLE_READER"));
    }

    private static OwnProfile profile(boolean minor) {
        var guardian = minor ? new Guardian("Carla Souza", "carla@example.com", null) : null;
        return new OwnProfile(Reader.reconstitute(ME, "Maria Silva", "12345678900", "maria@example.com",
                "11 91234-5678", LocalDate.of(1990, 5, 10), guardian, true, false), minor);
    }

    @Test
    void shouldShowTheOwnRegistrationWithTheDocumentMaskedAndNothingOfTheState() throws Exception {
        given(getUseCase.get(any())).willReturn(profile(false));

        var body = mockMvc.perform(get("/api/v1/me/profile").with(reader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Maria Silva"))
                .andExpect(jsonPath("$.document").value("*********00"))
                .andExpect(jsonPath("$.email").value("maria@example.com"))
                .andExpect(jsonPath("$.phone").value("11 91234-5678"))
                .andExpect(jsonPath("$.birthDate").value("1990-05-10"))
                .andExpect(jsonPath("$.minor").value(false))
                .andReturn().getResponse().getContentAsString();

        Map<String, Object> fields = JsonPath.read(body, "$");
        assertThat(fields.keySet()).containsExactlyInAnyOrder("name", "document", "email", "phone", "birthDate",
                "minor", "guardian");
        then(getUseCase).should().get(ME);
    }

    @Test
    void shouldShowTheGuardianOfAMinor() throws Exception {
        given(getUseCase.get(any())).willReturn(profile(true));

        mockMvc.perform(get("/api/v1/me/profile").with(reader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.minor").value(true))
                .andExpect(jsonPath("$.guardian.name").value("Carla Souza"))
                .andExpect(jsonPath("$.guardian.email").value("carla@example.com"));
    }

    @Test
    void shouldChangeTheContactDataOfTheReaderOfTheTokenIgnoringAnyIdentityFieldSent() throws Exception {
        given(updateUseCase.update(any())).willReturn(profile(false));

        mockMvc.perform(patch("/api/v1/me/profile").with(reader()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"21 98888-7777","email":"novo@example.com","currentPassword":"Cur#rent1Pass",
                                 "readerId":7,"name":"Outro Nome","document":"999","birthDate":"2000-01-01",
                                 "blocked":false,"active":false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Maria Silva"));

        var command = ArgumentCaptor.forClass(UpdateOwnProfileCommand.class);
        then(updateUseCase).should().update(command.capture());
        assertThat(command.getValue()).isEqualTo(new UpdateOwnProfileCommand(ME, "21 98888-7777",
                "novo@example.com", null, null, null, "Cur#rent1Pass"));
    }

    @Test
    void shouldPassTheGuardianFieldsToTheUseCase() throws Exception {
        given(updateUseCase.update(any())).willReturn(profile(true));

        mockMvc.perform(patch("/api/v1/me/profile").with(reader()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"guardian\":{\"name\":\"Carla\",\"email\":\"c@example.com\",\"phone\":\"11 90000-1111\"}}"))
                .andExpect(status().isOk());

        var command = ArgumentCaptor.forClass(UpdateOwnProfileCommand.class);
        then(updateUseCase).should().update(command.capture());
        assertThat(command.getValue()).isEqualTo(new UpdateOwnProfileCommand(ME, null, null, "Carla",
                "c@example.com", "11 90000-1111", null));
    }

    @Test
    void shouldRefuseABlankEmailWith400() throws Exception {
        mockMvc.perform(patch("/api/v1/me/profile").with(reader()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.errors[0].field").value("email"));

        verifyNoInteractions(updateUseCase);
    }

    @Test
    void shouldAnswer422ForABusinessRuleOfTheContactData() throws Exception {
        given(updateUseCase.update(any())).willThrow(new BusinessRuleException("a senha atual está incorreta"));

        mockMvc.perform(patch("/api/v1/me/profile").with(reader()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"novo@example.com\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("a senha atual está incorreta"));
    }

    @Test
    void shouldChangeThePasswordOfTheReaderOfTheTokenAndAnswerWithNoBody() throws Exception {
        mockMvc.perform(post("/api/v1/me/password").with(reader()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Cur#rent1Pass\",\"newPassword\":\"N3w-Pass#word\"}"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        then(passwordUseCase).should().change(new ChangeOwnPasswordCommand(ME, "Cur#rent1Pass", "N3w-Pass#word"));
    }

    @Test
    void shouldRefuseThePasswordChangeWithoutThePasswordsWith400() throws Exception {
        mockMvc.perform(post("/api/v1/me/password").with(reader()).contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));

        verifyNoInteractions(passwordUseCase);
    }

    @Test
    void shouldAnswer422WhenThePasswordIsRefused() throws Exception {
        org.mockito.BDDMockito.willThrow(new BusinessRuleException("a senha atual está incorreta"))
                .given(passwordUseCase).change(any());

        mockMvc.perform(post("/api/v1/me/password").with(reader()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"x\",\"newPassword\":\"y\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("a senha atual está incorreta"));
    }

    @Test
    void shouldNotEchoPasswordsInTheStringFormOfTheRequests() {
        assertThat(new ChangeOwnPasswordRequest("secret-1", "secret-2").toString()).doesNotContain("secret");
        assertThat(new UpdateOwnProfileRequest(null, "a@b.com", null, "secret-1").toString())
                .doesNotContain("secret").doesNotContain("a@b.com");
    }

    @Test
    @WithAdmin
    void shouldKeepAnAdministratorOut() throws Exception {
        mockMvc.perform(get("/api/v1/me/profile")).andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/me/profile").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/me/password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"x\",\"newPassword\":\"y\"}")).andExpect(status().isForbidden());

        verifyNoInteractions(getUseCase, updateUseCase, passwordUseCase);
    }

    @Test
    @WithOperator
    void shouldKeepAnOperatorOut() throws Exception {
        mockMvc.perform(get("/api/v1/me/profile")).andExpect(status().isForbidden());
    }

    @Test
    void shouldAnswer401WithoutAToken() throws Exception {
        mockMvc.perform(get("/api/v1/me/profile")).andExpect(status().isUnauthorized());
    }
}
