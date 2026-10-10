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

import com.riverfount.booksmanager.catalog.application.port.in.GetAuthorUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.RegisterAuthorUseCase;
import com.riverfount.booksmanager.catalog.domain.Author;
import com.riverfount.booksmanager.shared.adapter.in.web.GlobalExceptionHandler;
import java.util.Optional;
import org.junit.jupiter.api.Test;
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
}
