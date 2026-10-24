package com.riverfount.booksmanager.catalog.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.riverfount.booksmanager.security.configuration.MethodSecurityConfiguration;
import com.riverfount.booksmanager.WithOperator;
import com.riverfount.booksmanager.WithAdmin;
import com.riverfount.booksmanager.catalog.application.port.in.DeactivateAuthorUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.ReactivateAuthorUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.GetAuthorUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.ListAuthorsQuery;
import com.riverfount.booksmanager.catalog.application.port.in.ListAuthorsUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.RegisterAuthorUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.UpdateAuthorCommand;
import com.riverfount.booksmanager.catalog.application.port.in.UpdateAuthorUseCase;
import com.riverfount.booksmanager.catalog.domain.Author;
import com.riverfount.booksmanager.shared.adapter.in.web.GlobalExceptionHandler;
import com.riverfount.booksmanager.shared.domain.PageResult;
import com.riverfount.booksmanager.shared.domain.ResourceNotFoundException;
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
@Import({GlobalExceptionHandler.class, MethodSecurityConfiguration.class})
class AuthorControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RegisterAuthorUseCase registerAuthorUseCase;

    @MockitoBean
    private GetAuthorUseCase getAuthorUseCase;

    @MockitoBean
    private ListAuthorsUseCase listAuthorsUseCase;

    @MockitoBean
    private UpdateAuthorUseCase updateAuthorUseCase;

    @MockitoBean
    private DeactivateAuthorUseCase deactivateAuthorUseCase;

    @MockitoBean
    private ReactivateAuthorUseCase reactivateAuthorUseCase;

    @Test
    @WithOperator
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
    @WithOperator
    void shouldReturn400WithProblemDetailForBlankName() throws Exception {
        mockMvc.perform(post("/api/v1/authors")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")));
    }

    @Test
    @WithOperator
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
    @WithOperator
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
    @WithOperator
    void shouldFindExistingAuthorById() throws Exception {
        given(getAuthorUseCase.findById(eq(1L)))
                .willReturn(Optional.of(Author.reconstitute(1L, "Machado de Assis", true)));

        mockMvc.perform(get("/api/v1/authors/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Machado de Assis"));
    }

    @Test
    @WithOperator
    void shouldReturn404ForNonExistentId() throws Exception {
        given(getAuthorUseCase.findById(eq(99L))).willReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/authors/99"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")));
    }

    @Test
    @WithOperator
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
    @WithOperator
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
    @WithOperator
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
    @WithOperator
    void shouldReturn400ForPageSizeAbove100() throws Exception {
        mockMvc.perform(get("/api/v1/authors").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")))
                .andExpect(jsonPath("$.detail").value(containsString("100")));
    }

    @Test
    @WithOperator
    void shouldReturn400ForNegativePage() throws Exception {
        mockMvc.perform(get("/api/v1/authors").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")));
    }

    @Test
    @WithOperator
    void shouldReturn400ForUnknownSortField() throws Exception {
        mockMvc.perform(get("/api/v1/authors").param("sort", "birthDate,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")))
                .andExpect(jsonPath("$.detail").value(containsString("birthDate")));
    }

    @Test
    @WithOperator
    void shouldReturn400ForInvalidActiveValue() throws Exception {
        mockMvc.perform(get("/api/v1/authors").param("active", "maybe"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")));
    }

    @Test
    @WithOperator
    void shouldUpdateAuthorNameAndReturn200() throws Exception {
        given(updateAuthorUseCase.update(any())).willReturn(Author.reconstitute(1L, "Machado de Assis", true));

        mockMvc.perform(patch("/api/v1/authors/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Machado de Assis\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Machado de Assis"))
                .andExpect(jsonPath("$.active").value(true));

        then(updateAuthorUseCase).should().update(new UpdateAuthorCommand(1L, "Machado de Assis"));
    }

    @Test
    @WithOperator
    void shouldSendNullNameWhenItIsOmitted() throws Exception {
        given(updateAuthorUseCase.update(any())).willReturn(Author.reconstitute(1L, "Machado de Assis", true));

        mockMvc.perform(patch("/api/v1/authors/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        then(updateAuthorUseCase).should().update(new UpdateAuthorCommand(1L, null));
    }

    @Test
    @WithOperator
    void shouldReturn404WhenUpdatingNonExistentAuthor() throws Exception {
        given(updateAuthorUseCase.update(any())).willThrow(new ResourceNotFoundException("autor não encontrado: 99"));

        mockMvc.perform(patch("/api/v1/authors/99")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Machado de Assis\"}"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")));
    }

    @Test
    @WithOperator
    void shouldReturn400WhenUpdatingWithBlankName() throws Exception {
        mockMvc.perform(patch("/api/v1/authors/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")));

        then(updateAuthorUseCase).should(never()).update(any());
    }

    @Test
    @WithOperator
    void shouldReturn400WhenUpdatingWithEmptyName() throws Exception {
        mockMvc.perform(patch("/api/v1/authors/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")));
    }

    @Test
    @WithOperator
    void shouldReturn400WhenUpdatingWithNameLongerThanDomainLimit() throws Exception {
        var nameOneCharacterAboveTheLimit = "A".repeat(Author.MAX_NAME_LENGTH + 1);

        mockMvc.perform(patch("/api/v1/authors/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + nameOneCharacterAboveTheLimit + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")));
    }

    @Test
    @WithOperator
    void shouldReturn400WhenUpdatingWithMalformedJson() throws Exception {
        mockMvc.perform(patch("/api/v1/authors/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")));
    }

    @Test
    @WithOperator
    void shouldDeactivateAuthorAndReturn204WithoutBody() throws Exception {
        mockMvc.perform(delete("/api/v1/authors/1").with(csrf()))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        then(deactivateAuthorUseCase).should().deactivate(1L);
    }

    @Test
    @WithOperator
    void shouldReturn404WhenDeactivatingNonExistentAuthor() throws Exception {
        willThrow(new ResourceNotFoundException("autor não encontrado: 99"))
                .given(deactivateAuthorUseCase).deactivate(99L);

        mockMvc.perform(delete("/api/v1/authors/99").with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")));
    }

    @Test
    @WithOperator
    void shouldReturn400WhenDeactivatingWithNonNumericId() throws Exception {
        mockMvc.perform(delete("/api/v1/authors/abc").with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")));

        then(deactivateAuthorUseCase).should(never()).deactivate(any());
    }

    private ListAuthorsQuery capturedQuery() {
        var captor = ArgumentCaptor.forClass(ListAuthorsQuery.class);
        then(listAuthorsUseCase).should().list(captor.capture());
        return captor.getValue();
    }

    // authorization by profile (RNF08): the whole controller is for an administrator or an operator

    @Test
    @WithMockUser(roles = "USER")
    void shouldRefuseAnAuthenticatedUserWithoutAnAllowedProfileWith403() throws Exception {
        mockMvc.perform(get("/api/v1/authors"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.detail").value("Você não tem permissão para acessar este recurso."));
        mockMvc.perform(delete("/api/v1/authors/1").with(csrf())).andExpect(status().isForbidden());

        then(deactivateAuthorUseCase).should(never()).deactivate(any());
    }

    @Test
    @WithMockUser(roles = {})
    void shouldRefuseAnAuthenticatedUserWithNoProfileAtAllWith403() throws Exception {
        mockMvc.perform(delete("/api/v1/authors/1").with(csrf())).andExpect(status().isForbidden());

        then(deactivateAuthorUseCase).should(never()).deactivate(any());
    }

    @Test
    @WithAdmin
    void anAdministratorMayCallTheRoutesToo() throws Exception {
        mockMvc.perform(delete("/api/v1/authors/1").with(csrf())).andExpect(status().isNoContent());
    }

    @Test
    @WithOperator
    void anOperatorMayCallTheRoutes() throws Exception {
        mockMvc.perform(delete("/api/v1/authors/1").with(csrf())).andExpect(status().isNoContent());
    }

    // reactivation (RN14): only the administrator

    @Test
    @WithAdmin
    void anAdministratorShouldReactivateAndGetTheResource() throws Exception {
        given(reactivateAuthorUseCase.reactivate(1L)).willReturn(com.riverfount.booksmanager.catalog.domain.Author.reconstitute(1L, "Machado de Assis", true));

        mockMvc.perform(post("/api/v1/authors/1/reactivate").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.active").value(true))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.name").exists());

        then(reactivateAuthorUseCase).should().reactivate(1L);
    }

    @Test
    @WithOperator
    void anOperatorShouldNotReactivateAndGet403() throws Exception {
        mockMvc.perform(post("/api/v1/authors/1/reactivate").with(csrf()))
                .andExpect(status().isForbidden());

        then(reactivateAuthorUseCase).shouldHaveNoInteractions();
    }

    @Test
    void withoutATokenReactivatingShouldBe401() throws Exception {
        mockMvc.perform(post("/api/v1/authors/1/reactivate").with(csrf()))
                .andExpect(status().isUnauthorized());

        then(reactivateAuthorUseCase).shouldHaveNoInteractions();
    }

    @Test
    @WithAdmin
    void reactivatingSomethingThatDoesNotExistShouldBe404() throws Exception {
        willThrow(new ResourceNotFoundException("autor não encontrado: 99")).given(reactivateAuthorUseCase).reactivate(99L);

        mockMvc.perform(post("/api/v1/authors/99/reactivate").with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")));
    }
}
