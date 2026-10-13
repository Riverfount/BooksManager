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

import com.riverfount.booksmanager.catalog.application.port.in.DeactivateCategoryUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.GetCategoryUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.ListCategoriesQuery;
import com.riverfount.booksmanager.catalog.application.port.in.ListCategoriesUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.RegisterCategoryUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.UpdateCategoryCommand;
import com.riverfount.booksmanager.catalog.application.port.in.UpdateCategoryUseCase;
import com.riverfount.booksmanager.catalog.domain.Category;
import com.riverfount.booksmanager.shared.adapter.in.web.GlobalExceptionHandler;
import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
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

@WebMvcTest(CategoryController.class)
@Import(GlobalExceptionHandler.class)
class CategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RegisterCategoryUseCase registerCategoryUseCase;

    @MockitoBean
    private GetCategoryUseCase getCategoryUseCase;

    @MockitoBean
    private ListCategoriesUseCase listCategoriesUseCase;

    @MockitoBean
    private UpdateCategoryUseCase updateCategoryUseCase;

    @MockitoBean
    private DeactivateCategoryUseCase deactivateCategoryUseCase;

    @Test
    @WithMockUser
    void shouldRegisterCategoryAndReturn201WithLocation() throws Exception {
        given(registerCategoryUseCase.register(any())).willReturn(Category.reconstitute(1L, "Ficção", true));

        mockMvc.perform(post("/api/v1/categories")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ficção\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/categories/1")))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Ficção"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    @WithMockUser
    void shouldReturn400WithProblemDetailForBlankName() throws Exception {
        mockMvc.perform(post("/api/v1/categories")
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
        mockMvc.perform(post("/api/v1/categories")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" Ficção\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")));
    }

    @Test
    @WithMockUser
    void shouldReturn400ForNameLongerThanDomainLimit() throws Exception {
        // the DTO's limit needs to be the same as the business rule's
        // (Category.MAX_NAME_LENGTH), not a separately duplicated number
        var nameOneCharacterAboveTheLimit = "A".repeat(Category.MAX_NAME_LENGTH + 1);

        mockMvc.perform(post("/api/v1/categories")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + nameOneCharacterAboveTheLimit + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")));
    }

    @Test
    @WithMockUser
    void shouldReturn422WithClearMessageForDuplicateName() throws Exception {
        given(registerCategoryUseCase.register(any()))
                .willThrow(new BusinessRuleException("já existe uma categoria com o nome 'Ficção'"));

        mockMvc.perform(post("/api/v1/categories")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ficção\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")))
                .andExpect(jsonPath("$.detail").value("já existe uma categoria com o nome 'Ficção'"));
    }

    @Test
    @WithMockUser
    void shouldFindExistingCategoryById() throws Exception {
        given(getCategoryUseCase.findById(eq(1L)))
                .willReturn(Optional.of(Category.reconstitute(1L, "Ficção", true)));

        mockMvc.perform(get("/api/v1/categories/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Ficção"));
    }

    @Test
    @WithMockUser
    void shouldReturn404ForNonExistentId() throws Exception {
        given(getCategoryUseCase.findById(eq(99L))).willReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/categories/99"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")));
    }

    @Test
    @WithMockUser
    void shouldListCategoriesWithPaginationMetadata() throws Exception {
        given(listCategoriesUseCase.list(any())).willReturn(new PageResult<>(
                List.of(Category.reconstitute(1L, "Ficção", true), Category.reconstitute(2L, "Romance", true)),
                0, 2, 3));

        mockMvc.perform(get("/api/v1/categories").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id").value(1))
                .andExpect(jsonPath("$.items[0].name").value("Ficção"))
                .andExpect(jsonPath("$.items[0].active").value(true))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    @WithMockUser
    void shouldListOnlyActiveCategoriesSortedByNameByDefault() throws Exception {
        given(listCategoriesUseCase.list(any())).willReturn(new PageResult<>(List.of(), 0, 20, 0));

        mockMvc.perform(get("/api/v1/categories")).andExpect(status().isOk());

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
        given(listCategoriesUseCase.list(any())).willReturn(new PageResult<>(List.of(), 1, 5, 0));

        mockMvc.perform(get("/api/v1/categories")
                        .param("name", "ficção")
                        .param("active", "false")
                        .param("page", "1")
                        .param("size", "5")
                        .param("sort", "id,desc"))
                .andExpect(status().isOk());

        var query = capturedQuery();
        assertThat(query.name()).isEqualTo("ficção");
        assertThat(query.active()).isFalse();
        assertThat(query.page().page()).isEqualTo(1);
        assertThat(query.page().size()).isEqualTo(5);
        assertThat(query.page().sortBy()).isEqualTo("id");
        assertThat(query.page().direction()).isEqualTo(SortDirection.DESC);
    }

    @Test
    @WithMockUser
    void shouldReturn400ForPageSizeAbove100() throws Exception {
        mockMvc.perform(get("/api/v1/categories").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")))
                .andExpect(jsonPath("$.detail").value(containsString("100")));
    }

    @Test
    @WithMockUser
    void shouldReturn400ForNegativePage() throws Exception {
        mockMvc.perform(get("/api/v1/categories").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")));
    }

    @Test
    @WithMockUser
    void shouldReturn400ForUnknownSortField() throws Exception {
        mockMvc.perform(get("/api/v1/categories").param("sort", "createdAt,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")))
                .andExpect(jsonPath("$.detail").value(containsString("createdAt")));
    }

    @Test
    @WithMockUser
    void shouldReturn400ForInvalidActiveValue() throws Exception {
        mockMvc.perform(get("/api/v1/categories").param("active", "maybe"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")));
    }

    @Test
    @WithMockUser
    void shouldUpdateCategoryNameAndReturn200() throws Exception {
        given(updateCategoryUseCase.update(any())).willReturn(Category.reconstitute(1L, "Ficção", true));

        mockMvc.perform(patch("/api/v1/categories/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ficção\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Ficção"))
                .andExpect(jsonPath("$.active").value(true));

        then(updateCategoryUseCase).should().update(new UpdateCategoryCommand(1L, "Ficção"));
    }

    @Test
    @WithMockUser
    void shouldSendNullNameWhenItIsOmitted() throws Exception {
        given(updateCategoryUseCase.update(any())).willReturn(Category.reconstitute(1L, "Ficção", true));

        mockMvc.perform(patch("/api/v1/categories/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        then(updateCategoryUseCase).should().update(new UpdateCategoryCommand(1L, null));
    }

    @Test
    @WithMockUser
    void shouldReturn422WhenRenamingToNameUsedByAnotherCategory() throws Exception {
        given(updateCategoryUseCase.update(any()))
                .willThrow(new BusinessRuleException("já existe uma categoria com o nome 'Romance'"));

        mockMvc.perform(patch("/api/v1/categories/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Romance\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")))
                .andExpect(jsonPath("$.detail").value(containsString("Romance")));
    }

    @Test
    @WithMockUser
    void shouldReturn404WhenUpdatingNonExistentCategory() throws Exception {
        given(updateCategoryUseCase.update(any()))
                .willThrow(new ResourceNotFoundException("categoria não encontrada: 99"));

        mockMvc.perform(patch("/api/v1/categories/99")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ficção\"}"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")));
    }

    @Test
    @WithMockUser
    void shouldReturn400WhenUpdatingWithBlankName() throws Exception {
        mockMvc.perform(patch("/api/v1/categories/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")));

        then(updateCategoryUseCase).should(never()).update(any());
    }

    @Test
    @WithMockUser
    void shouldReturn400WhenUpdatingWithEmptyName() throws Exception {
        mockMvc.perform(patch("/api/v1/categories/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")));
    }

    @Test
    @WithMockUser
    void shouldReturn400WhenUpdatingWithNameLongerThanDomainLimit() throws Exception {
        var nameOneCharacterAboveTheLimit = "A".repeat(Category.MAX_NAME_LENGTH + 1);

        mockMvc.perform(patch("/api/v1/categories/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + nameOneCharacterAboveTheLimit + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")));
    }

    @Test
    @WithMockUser
    void shouldReturn400WhenUpdatingWithMalformedJson() throws Exception {
        mockMvc.perform(patch("/api/v1/categories/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")));
    }

    @Test
    @WithMockUser
    void shouldDeactivateCategoryAndReturn204WithoutBody() throws Exception {
        mockMvc.perform(delete("/api/v1/categories/1").with(csrf()))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        then(deactivateCategoryUseCase).should().deactivate(1L);
    }

    @Test
    @WithMockUser
    void shouldReturn404WhenDeactivatingNonExistentCategory() throws Exception {
        willThrow(new ResourceNotFoundException("categoria não encontrada: 99"))
                .given(deactivateCategoryUseCase).deactivate(99L);

        mockMvc.perform(delete("/api/v1/categories/99").with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")));
    }

    @Test
    @WithMockUser
    void shouldReturn400WhenDeactivatingWithNonNumericId() throws Exception {
        mockMvc.perform(delete("/api/v1/categories/abc").with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")));

        then(deactivateCategoryUseCase).should(never()).deactivate(any());
    }

    private ListCategoriesQuery capturedQuery() {
        var captor = ArgumentCaptor.forClass(ListCategoriesQuery.class);
        then(listCategoriesUseCase).should().list(captor.capture());
        return captor.getValue();
    }
}
