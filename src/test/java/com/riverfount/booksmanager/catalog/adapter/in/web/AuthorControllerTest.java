package com.riverfount.booksmanager.catalog.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.riverfount.booksmanager.catalog.application.port.in.GetAuthorUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.ListAuthorsQuery;
import com.riverfount.booksmanager.catalog.application.port.in.ListAuthorsUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.RegisterAuthorUseCase;
import com.riverfount.booksmanager.catalog.domain.Author;
import com.riverfount.booksmanager.shared.adapter.in.web.GlobalExceptionHandler;
import com.riverfount.booksmanager.shared.domain.PageResult;
import com.riverfount.booksmanager.shared.domain.SortDirection;
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

@WebMvcTest(AuthorController.class)
@Import(GlobalExceptionHandler.class)
class AuthorControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RegisterAuthorUseCase registerAuthorUseCase;

    @MockitoBean
    private GetAuthorUseCase getAuthorUseCase;

    @MockitoBean
    private ListAuthorsUseCase listAuthorsUseCase;

    @Test
    @WithMockUser
    void shouldRegisterAuthorAndReturn201WithLocation() throws Exception {
        given(registerAuthorUseCase.register(any())).willReturn(Author.reconstitute(1L, "Machado de Assis", true));

        mockMvc.perform(post("/api/v1/authors")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Machado de Assis\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/authors/1")))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Machado de Assis"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    @WithMockUser
    void shouldReturn400WithProblemDetailForBlankName() throws Exception {
        mockMvc.perform(post("/api/v1/authors")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")));
    }

    @Test
    @WithMockUser
    void shouldReturn400ForNameWithLeadingSpace() throws Exception {
        // the DTO needs to catch this as a format error (400), and not let
        // it fall through to the domain as a business rule (422)
        mockMvc.perform(post("/api/v1/authors")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" Machado de Assis\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")));
    }

    @Test
    @WithMockUser
    void shouldReturn400ForNameLongerThanDomainLimit() throws Exception {
        var nameOneCharacterAboveTheLimit = "A".repeat(Author.MAX_NAME_LENGTH + 1);

        mockMvc.perform(post("/api/v1/authors")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + nameOneCharacterAboveTheLimit + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")));
    }

    @Test
    @WithMockUser
    void shouldFindExistingAuthorById() throws Exception {
        given(getAuthorUseCase.findById(eq(1L)))
                .willReturn(Optional.of(Author.reconstitute(1L, "Machado de Assis", true)));

        mockMvc.perform(get("/api/v1/authors/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Machado de Assis"));
    }

    @Test
    @WithMockUser
    void shouldReturn404ForNonExistentId() throws Exception {
        given(getAuthorUseCase.findById(eq(99L))).willReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/authors/99"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")));
    }

    @Test
    @WithMockUser
    void shouldListAuthorsWithPaginationMetadata() throws Exception {
        given(listAuthorsUseCase.list(any())).willReturn(new PageResult<>(
                List.of(Author.reconstitute(1L, "Clarice Lispector", true),
                        Author.reconstitute(2L, "Machado de Assis", true)),
                0, 2, 3));

        mockMvc.perform(get("/api/v1/authors").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id").value(1))
                .andExpect(jsonPath("$.items[0].name").value("Clarice Lispector"))
                .andExpect(jsonPath("$.items[0].active").value(true))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    @WithMockUser
    void shouldListOnlyActiveAuthorsSortedByNameByDefault() throws Exception {
        given(listAuthorsUseCase.list(any())).willReturn(new PageResult<>(List.of(), 0, 20, 0));

        mockMvc.perform(get("/api/v1/authors")).andExpect(status().isOk());

        var query = capturedQuery();
        assertThat(query.name()).isNull();
        assertThat(query.active()).isTrue();
        assertThat(query.page().page()).isZero();
        assertThat(query.page().size()).isEqualTo(20);
        assertThat(query.page().sortBy()).isEqualTo("name");
        assertThat(query.page().direction()).isEqualTo(SortDirection.ASC);
    }

    @Test
    @WithMockUser
    void shouldPassFiltersPaginationAndSortToTheUseCase() throws Exception {
        given(listAuthorsUseCase.list(any())).willReturn(new PageResult<>(List.of(), 1, 5, 0));

        mockMvc.perform(get("/api/v1/authors")
                        .param("name", "assis")
                        .param("active", "false")
                        .param("page", "1")
                        .param("size", "5")
                        .param("sort", "id,desc"))
                .andExpect(status().isOk());

        var query = capturedQuery();
        assertThat(query.name()).isEqualTo("assis");
        assertThat(query.active()).isFalse();
        assertThat(query.page().page()).isEqualTo(1);
        assertThat(query.page().size()).isEqualTo(5);
        assertThat(query.page().sortBy()).isEqualTo("id");
        assertThat(query.page().direction()).isEqualTo(SortDirection.DESC);
    }

    @Test
    @WithMockUser
    void shouldReturn400ForPageSizeAbove100() throws Exception {
        mockMvc.perform(get("/api/v1/authors").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")))
                .andExpect(jsonPath("$.detail").value(containsString("100")));
    }

    @Test
    @WithMockUser
    void shouldReturn400ForNegativePage() throws Exception {
        mockMvc.perform(get("/api/v1/authors").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")));
    }

    @Test
    @WithMockUser
    void shouldReturn400ForUnknownSortField() throws Exception {
        mockMvc.perform(get("/api/v1/authors").param("sort", "birthDate,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")))
                .andExpect(jsonPath("$.detail").value(containsString("birthDate")));
    }

    @Test
    @WithMockUser
    void shouldReturn400ForInvalidActiveValue() throws Exception {
        mockMvc.perform(get("/api/v1/authors").param("active", "maybe"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")));
    }

    private ListAuthorsQuery capturedQuery() {
        var captor = ArgumentCaptor.forClass(ListAuthorsQuery.class);
        then(listAuthorsUseCase).should().list(captor.capture());
        return captor.getValue();
    }
}
