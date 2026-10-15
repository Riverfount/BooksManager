package com.riverfount.booksmanager.user.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.riverfount.booksmanager.shared.adapter.in.web.GlobalExceptionHandler;
import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import com.riverfount.booksmanager.shared.domain.PageResult;
import com.riverfount.booksmanager.shared.domain.ResourceNotFoundException;
import com.riverfount.booksmanager.shared.domain.SortDirection;
import com.riverfount.booksmanager.user.application.port.in.DeactivateReaderUseCase;
import com.riverfount.booksmanager.user.application.port.in.GetReaderUseCase;
import com.riverfount.booksmanager.user.application.port.in.ListReadersQuery;
import com.riverfount.booksmanager.user.application.port.in.ListReadersUseCase;
import com.riverfount.booksmanager.user.application.port.in.RegisterReaderCommand;
import com.riverfount.booksmanager.user.application.port.in.RegisterReaderUseCase;
import com.riverfount.booksmanager.user.application.port.in.UpdateReaderCommand;
import com.riverfount.booksmanager.user.application.port.in.UpdateReaderUseCase;
import com.riverfount.booksmanager.user.domain.Guardian;
import com.riverfount.booksmanager.user.domain.Reader;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
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

    @MockitoBean
    private GetReaderUseCase getReaderUseCase;

    @MockitoBean
    private ListReadersUseCase listReadersUseCase;

    @MockitoBean
    private UpdateReaderUseCase updateReaderUseCase;

    @MockitoBean
    private DeactivateReaderUseCase deactivateReaderUseCase;

    private MockHttpServletRequestBuilder update(long id, String json) {
        return patch("/api/v1/readers/" + id).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json);
    }

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

    // query (HU25)

    @Test
    @WithMockUser
    void shouldReturnReaderWithFullDocumentBirthDateAndState() throws Exception {
        given(getReaderUseCase.findById(1L)).willReturn(Optional.of(Reader.reconstitute(1L, "Maria Silva",
                "12345678900", "maria@example.com", "11 91234-5678", LocalDate.of(1990, 5, 10), null, true,
                false)));

        mockMvc.perform(get("/api/v1/readers/1"))
                .andExpect(status().isOk())
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
    void shouldReturnTheGuardianOfAMinorReader() throws Exception {
        given(getReaderUseCase.findById(2L)).willReturn(Optional.of(Reader.reconstitute(2L, "Júlia", "MAT1",
                "julia@example.com", null, LocalDate.of(2015, 3, 1),
                new Guardian("Ana Souza", "ana@example.com", "11 99999-0000"), true, false)));

        mockMvc.perform(get("/api/v1/readers/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.guardian.name").value("Ana Souza"))
                .andExpect(jsonPath("$.guardian.email").value("ana@example.com"))
                .andExpect(jsonPath("$.guardian.phone").value("11 99999-0000"));
    }

    @Test
    @WithMockUser
    void shouldReturn200ForInactiveAndBlockedReaderShowingTheState() throws Exception {
        given(getReaderUseCase.findById(3L)).willReturn(Optional.of(Reader.reconstitute(3L, "Pedro", "999",
                "pedro@example.com", null, LocalDate.of(1985, 1, 1), null, false, true)));

        mockMvc.perform(get("/api/v1/readers/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.blocked").value(true));
    }

    @Test
    @WithMockUser
    void shouldReturn404WithProblemDetailForNonExistentId() throws Exception {
        given(getReaderUseCase.findById(99L)).willReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/readers/99"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.detail").value("leitor não encontrado: 99"));
    }

    // listing (HU25, RNF14)

    private ListReadersQuery capturedQuery() {
        var captor = ArgumentCaptor.forClass(ListReadersQuery.class);
        then(listReadersUseCase).should().list(captor.capture());
        return captor.getValue();
    }

    @Test
    @WithMockUser
    void shouldListReadersWithTheMaskedDocumentAndNoSensitiveDetail() throws Exception {
        var minor = Reader.reconstitute(2L, "Júlia", "MAT123", "julia@example.com", "11 91234-5678",
                LocalDate.of(2015, 3, 1), new Guardian("Ana Souza", "ana@example.com", "11 99999-0000"), true,
                false);
        var adult = Reader.reconstitute(1L, "Maria Silva", "12345678900", "maria@example.com", null,
                LocalDate.of(1990, 5, 10), null, true, true);
        given(listReadersUseCase.list(any())).willReturn(new PageResult<>(List.of(adult, minor), 0, 20, 2));

        var body = mockMvc.perform(get("/api/v1/readers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id").value(1))
                .andExpect(jsonPath("$.items[0].name").value("Maria Silva"))
                .andExpect(jsonPath("$.items[0].maskedDocument").value("*********00"))
                .andExpect(jsonPath("$.items[0].email").value("maria@example.com"))
                .andExpect(jsonPath("$.items[0].active").value(true))
                .andExpect(jsonPath("$.items[0].blocked").value(true))
                .andExpect(jsonPath("$.items[1].maskedDocument").value("****23"))
                .andExpect(jsonPath("$.items[0].document").doesNotExist())
                .andExpect(jsonPath("$.items[0].birthDate").doesNotExist())
                .andExpect(jsonPath("$.items[1].birthDate").doesNotExist())
                .andExpect(jsonPath("$.items[1].guardian").doesNotExist())
                .andExpect(jsonPath("$.items[1].phone").doesNotExist())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("12345678900").doesNotContain("MAT123").doesNotContain("Ana Souza")
                .doesNotContain("1990-05-10").doesNotContain("2015-03-01");
    }

    @Test
    @WithMockUser
    void shouldListOnlyActiveReadersSortedByNameByDefault() throws Exception {
        given(listReadersUseCase.list(any())).willReturn(new PageResult<>(List.of(), 0, 20, 0));

        mockMvc.perform(get("/api/v1/readers")).andExpect(status().isOk());

        var query = capturedQuery();
        assertThat(query.active()).isTrue();
        assertThat(query.blocked()).isNull();
        assertThat(query.name()).isNull();
        assertThat(query.document()).isNull();
        assertThat(query.page().page()).isZero();
        assertThat(query.page().size()).isEqualTo(20);
        assertThat(query.page().sortBy()).isEqualTo("name");
        assertThat(query.page().direction()).isEqualTo(SortDirection.ASC);
    }

    @Test
    @WithMockUser
    void shouldPassFiltersPaginationAndSortToTheUseCase() throws Exception {
        given(listReadersUseCase.list(any())).willReturn(new PageResult<>(List.of(), 1, 5, 0));

        mockMvc.perform(get("/api/v1/readers")
                        .param("name", "maria")
                        .param("document", "123.456.789-00")
                        .param("active", "false")
                        .param("blocked", "true")
                        .param("page", "1")
                        .param("size", "5")
                        .param("sort", "id,desc"))
                .andExpect(status().isOk());

        var query = capturedQuery();
        assertThat(query.name()).isEqualTo("maria");
        assertThat(query.document()).isEqualTo("123.456.789-00");
        assertThat(query.active()).isFalse();
        assertThat(query.blocked()).isTrue();
        assertThat(query.page().page()).isEqualTo(1);
        assertThat(query.page().size()).isEqualTo(5);
        assertThat(query.page().sortBy()).isEqualTo("id");
        assertThat(query.page().direction()).isEqualTo(SortDirection.DESC);
    }

    @Test
    @WithMockUser
    void shouldReturn400WhenTheSizeIsAbove100() throws Exception {
        mockMvc.perform(get("/api/v1/readers").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));

        then(listReadersUseCase).should(never()).list(any());
    }

    @Test
    @WithMockUser
    void shouldReturn400ForInvalidPaginationSortOrFilter() throws Exception {
        mockMvc.perform(get("/api/v1/readers").param("page", "-1")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/readers").param("size", "0")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/readers").param("sort", "birthDate,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        mockMvc.perform(get("/api/v1/readers").param("sort", "name,sideways"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/readers").param("blocked", "maybe")).andExpect(status().isBadRequest());

        then(listReadersUseCase).should(never()).list(any());
    }

    // partial update (HU26)

    private static Reader updatedReader() {
        return Reader.reconstitute(1L, "Maria Souza", "12345678900", "maria@example.com", "11 91234-5678",
                LocalDate.of(1990, 5, 10), null, true, false);
    }

    @Test
    @WithMockUser
    void shouldUpdateAndReturn200WithTheReader() throws Exception {
        given(updateReaderUseCase.update(any())).willReturn(updatedReader());

        mockMvc.perform(update(1, "{\"name\":\"Maria Souza\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Maria Souza"))
                .andExpect(jsonPath("$.document").value("12345678900"))
                .andExpect(jsonPath("$.birthDate").value("1990-05-10"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.blocked").value(false));
    }

    @Test
    @WithMockUser
    void shouldSendOnlyTheSentFieldsToTheUseCase() throws Exception {
        given(updateReaderUseCase.update(any())).willReturn(updatedReader());

        mockMvc.perform(update(1, "{\"name\":\"Maria Souza\"}")).andExpect(status().isOk());

        var captor = ArgumentCaptor.forClass(UpdateReaderCommand.class);
        then(updateReaderUseCase).should().update(captor.capture());
        assertThat(captor.getValue()).isEqualTo(new UpdateReaderCommand(1L, "Maria Souza", null, null, null, null,
                null, null, null));
    }

    @Test
    @WithMockUser
    void shouldMapEveryFieldIncludingTheGuardian() throws Exception {
        given(updateReaderUseCase.update(any())).willReturn(updatedReader());

        mockMvc.perform(update(1, """
                {"name":"Júlia","document":"MAT-2","email":"j@example.com","phone":"11 91234-5678",
                 "birthDate":"2015-03-01",
                 "guardian":{"name":"Ana Souza","email":"ana@example.com","phone":"11 99999-0000"}}"""))
                .andExpect(status().isOk());

        var captor = ArgumentCaptor.forClass(UpdateReaderCommand.class);
        then(updateReaderUseCase).should().update(captor.capture());
        assertThat(captor.getValue()).isEqualTo(new UpdateReaderCommand(1L, "Júlia", "MAT-2", "j@example.com",
                "11 91234-5678", LocalDate.of(2015, 3, 1), "Ana Souza", "ana@example.com", "11 99999-0000"));
    }

    @Test
    @WithMockUser
    void shouldIgnoreFieldsThatAreNotEditableHere() throws Exception {
        given(updateReaderUseCase.update(any())).willReturn(updatedReader());

        mockMvc.perform(update(1, "{\"name\":\"Maria Souza\",\"active\":false,\"blocked\":true,\"id\":99}"))
                .andExpect(status().isOk());

        var captor = ArgumentCaptor.forClass(UpdateReaderCommand.class);
        then(updateReaderUseCase).should().update(captor.capture());
        assertThat(captor.getValue().id()).isEqualTo(1L);
        assertThat(captor.getValue()).isEqualTo(new UpdateReaderCommand(1L, "Maria Souza", null, null, null, null,
                null, null, null));
    }

    @Test
    @WithMockUser
    void shouldReturn400WhenASentFieldIsBlankOrHasSurroundingSpaces() throws Exception {
        for (var json : new String[] {"{\"name\":\"  \"}", "{\"name\":\" Maria\"}", "{\"document\":\"\"}",
                "{\"email\":\" \"}"}) {
            mockMvc.perform(update(1, json))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        }

        then(updateReaderUseCase).should(never()).update(any());
    }

    @Test
    @WithMockUser
    void shouldReturn400OnUpdateWhenTheBirthDateIsNotAnIsoDate() throws Exception {
        mockMvc.perform(update(1, "{\"birthDate\":\"10/05/1990\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void shouldReturn404ForANonExistentReader() throws Exception {
        given(updateReaderUseCase.update(any())).willThrow(new ResourceNotFoundException("leitor não encontrado: 99"));

        mockMvc.perform(update(99, "{\"name\":\"Maria\"}"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.detail").value("leitor não encontrado: 99"));
    }

    @Test
    @WithMockUser
    void shouldReturn422WhenTheDocumentBelongsToAnotherReader() throws Exception {
        given(updateReaderUseCase.update(any()))
                .willThrow(new BusinessRuleException("já existe um leitor com o documento '12345678900'"));

        mockMvc.perform(update(1, "{\"document\":\"123.456.789-00\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.detail").value(containsString("já existe um leitor")));
    }

    @Test
    @WithMockUser
    void shouldReturn422WhenTheEditLeavesAMinorWithoutTheGuardian() throws Exception {
        given(updateReaderUseCase.update(any())).willThrow(
                new BusinessRuleException("o responsável é obrigatório para leitor menor de 18 anos"));

        mockMvc.perform(update(1, "{\"birthDate\":\"2015-03-01\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("responsável")));
    }

    // deactivation (HU27, RN14)

    @Test
    @WithMockUser
    void shouldDeactivateReaderAndReturn204WithoutBody() throws Exception {
        mockMvc.perform(delete("/api/v1/readers/1").with(csrf()))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        then(deactivateReaderUseCase).should().deactivate(1L);
    }

    @Test
    @WithMockUser
    void deactivatingAnAlreadyInactiveReaderShouldAlsoReturn204() throws Exception {
        mockMvc.perform(delete("/api/v1/readers/1").with(csrf())).andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/readers/1").with(csrf())).andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser
    void shouldReturn404WhenDeactivatingANonExistentReader() throws Exception {
        willThrow(new ResourceNotFoundException("leitor não encontrado: 99"))
                .given(deactivateReaderUseCase).deactivate(99L);

        mockMvc.perform(delete("/api/v1/readers/99").with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.detail").value("leitor não encontrado: 99"));
    }

    @Test
    @WithMockUser
    void shouldReturn400WhenDeactivatingWithANonNumericId() throws Exception {
        mockMvc.perform(delete("/api/v1/readers/abc").with(csrf())).andExpect(status().isBadRequest());

        then(deactivateReaderUseCase).should(never()).deactivate(any());
    }
}
