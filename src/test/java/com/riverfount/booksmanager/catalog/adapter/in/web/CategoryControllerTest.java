package com.riverfount.booksmanager.catalog.adapter.in.web;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.riverfount.booksmanager.catalog.application.port.in.GetCategoryUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.RegisterCategoryUseCase;
import com.riverfount.booksmanager.catalog.domain.Category;
import com.riverfount.booksmanager.shared.adapter.in.web.GlobalExceptionHandler;
import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
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
}
