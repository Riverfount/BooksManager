package com.riverfount.booksmanager.user.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
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

import com.riverfount.booksmanager.shared.adapter.in.web.GlobalExceptionHandler;
import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import com.riverfount.booksmanager.user.application.port.in.RegisterReaderCommand;
import com.riverfount.booksmanager.user.application.port.in.RegisterReaderUseCase;
import com.riverfount.booksmanager.user.domain.Guardian;
import com.riverfount.booksmanager.user.domain.Reader;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(ReaderController.class)
@Import(GlobalExceptionHandler.class)
class ReaderControllerTest {

    private static final String ADULT_JSON = """
            {"name":"Maria Silva","document":"123.456.789-00","email":"maria@example.com",
             "phone":"11 91234-5678","birthDate":"1990-05-10"}""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RegisterReaderUseCase registerReaderUseCase;

    private MockHttpServletRequestBuilder register(String json) {
        return post("/api/v1/readers").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json);
    }

    @Test
    @WithMockUser
    void shouldRegisterReaderAndReturn201WithLocationAndTheFullDocument() throws Exception {
        given(registerReaderUseCase.register(any())).willReturn(Reader.reconstitute(1L, "Maria Silva",
                "12345678900", "maria@example.com", "11 91234-5678", LocalDate.of(1990, 5, 10), null, true,
                false));

        mockMvc.perform(register(ADULT_JSON))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/readers/1")))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Maria Silva"))
                .andExpect(jsonPath("$.document").value("12345678900"))
                .andExpect(jsonPath("$.email").value("maria@example.com"))
                .andExpect(jsonPath("$.phone").value("11 91234-5678"))
                .andExpect(jsonPath("$.birthDate").value("1990-05-10"))
                .andExpect(jsonPath("$.guardian").doesNotExist())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.blocked").value(false));
    }

    @Test
    @WithMockUser
    void shouldPassTheRequestToTheUseCaseAsACommand() throws Exception {
        given(registerReaderUseCase.register(any())).willReturn(Reader.reconstitute(1L, "Júlia", "MAT1",
                "julia@example.com", null, LocalDate.of(2015, 3, 1),
                new Guardian("Ana Souza", "ana@example.com", "11 99999-0000"), true, false));

        mockMvc.perform(register("""
                {"name":"Júlia","document":"MAT-1","email":"julia@example.com","birthDate":"2015-03-01",
                 "guardian":{"name":"Ana Souza","email":"ana@example.com","phone":"11 99999-0000"}}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.guardian.name").value("Ana Souza"))
                .andExpect(jsonPath("$.guardian.email").value("ana@example.com"))
                .andExpect(jsonPath("$.guardian.phone").value("11 99999-0000"));

        var captor = ArgumentCaptor.forClass(RegisterReaderCommand.class);
        then(registerReaderUseCase).should().register(captor.capture());
        assertThat(captor.getValue()).isEqualTo(new RegisterReaderCommand("Júlia", "MAT-1",
                "julia@example.com", null, LocalDate.of(2015, 3, 1), "Ana Souza", "ana@example.com",
                "11 99999-0000"));
    }

    @Test
    @WithMockUser
    void shouldSendNoGuardianFieldsWhenTheRequestHasNone() throws Exception {
        given(registerReaderUseCase.register(any())).willReturn(Reader.reconstitute(1L, "Maria Silva",
                "12345678900", "maria@example.com", null, LocalDate.of(1990, 5, 10), null, true, false));

        mockMvc.perform(register(ADULT_JSON)).andExpect(status().isCreated());

        var captor = ArgumentCaptor.forClass(RegisterReaderCommand.class);
        then(registerReaderUseCase).should().register(captor.capture());
        assertThat(captor.getValue().guardianName()).isNull();
        assertThat(captor.getValue().guardianEmail()).isNull();
        assertThat(captor.getValue().guardianPhone()).isNull();
    }

    @Test
    @WithMockUser
    void shouldReturn400WhenTheEmailIsMissing() throws Exception {
        mockMvc.perform(register("""
                {"name":"Maria","document":"123","birthDate":"1990-05-10"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));

        then(registerReaderUseCase).should(never()).register(any());
    }

    @Test
    @WithMockUser
    void shouldReturn400WhenTheBirthDateIsMissing() throws Exception {
        mockMvc.perform(register("""
                {"name":"Maria","document":"123","email":"maria@example.com"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));

        then(registerReaderUseCase).should(never()).register(any());
    }

    @Test
    @WithMockUser
    void shouldReturn400WhenTheNameOrTheDocumentIsBlank() throws Exception {
        mockMvc.perform(register("""
                {"name":"  ","document":"123","email":"maria@example.com","birthDate":"1990-05-10"}"""))
                .andExpect(status().isBadRequest());
        mockMvc.perform(register("""
                {"name":"Maria","document":"","email":"maria@example.com","birthDate":"1990-05-10"}"""))
                .andExpect(status().isBadRequest());

        then(registerReaderUseCase).should(never()).register(any());
    }

    @Test
    @WithMockUser
    void shouldReturn400WhenTheNameHasSurroundingSpaces() throws Exception {
        mockMvc.perform(register("""
                {"name":" Maria","document":"123","email":"maria@example.com","birthDate":"1990-05-10"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void shouldReturn400WhenTheBirthDateIsNotAnIsoDate() throws Exception {
        mockMvc.perform(register("""
                {"name":"Maria","document":"123","email":"maria@example.com","birthDate":"10/05/1990"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    @WithMockUser
    void shouldReturn422WithProblemDetailWhenTheDocumentIsDuplicate() throws Exception {
        given(registerReaderUseCase.register(any()))
                .willThrow(new BusinessRuleException("já existe um leitor com o documento '12345678900'"));

        mockMvc.perform(register(ADULT_JSON))
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.detail").value("já existe um leitor com o documento '12345678900'"));
    }

    @Test
    @WithMockUser
    void shouldReturn422WhenAMinorHasNoGuardian() throws Exception {
        given(registerReaderUseCase.register(any())).willThrow(
                new BusinessRuleException("o responsável é obrigatório para leitor menor de 18 anos"));

        mockMvc.perform(register("""
                {"name":"Júlia","document":"MAT-1","email":"julia@example.com","birthDate":"2015-03-01"}"""))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("responsável")));
    }
}
