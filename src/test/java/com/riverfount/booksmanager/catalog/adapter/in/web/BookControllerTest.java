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

import com.riverfount.booksmanager.catalog.application.port.in.GetBookUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.RegisterBookUseCase;
import com.riverfount.booksmanager.catalog.domain.Book;
import com.riverfount.booksmanager.catalog.domain.Isbn;
import com.riverfount.booksmanager.shared.adapter.in.web.GlobalExceptionHandler;
import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(BookController.class)
@Import(GlobalExceptionHandler.class)
class BookControllerTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-10-10T00:00:00Z"), ZoneOffset.UTC);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RegisterBookUseCase registerBookUseCase;

    @MockitoBean
    private GetBookUseCase getBookUseCase;

    private Book sampleBook() {
        return Book.create(new Isbn("9788533302273"), "Dom Casmurro", "Editora X", 1899, 1L, Set.of(10L),
                FIXED_CLOCK);
    }

    @Test
    @WithMockUser
    void shouldRegisterBookAndReturn201WithLocation() throws Exception {
        given(registerBookUseCase.register(any())).willReturn(sampleBook());

        mockMvc.perform(post("/api/v1/books")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"isbn":"9788533302273","title":"Dom Casmurro","publisher":"Editora X",
                                 "publicationYear":1899,"categoryId":1,"authorIds":[10]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/books/")))
                .andExpect(jsonPath("$.isbn").value("9788533302273"))
                .andExpect(jsonPath("$.title").value("Dom Casmurro"))
                .andExpect(jsonPath("$.categoryId").value(1))
                .andExpect(jsonPath("$.authorIds[0]").value(10));
    }

    @Test
    @WithMockUser
    void shouldReturn400WithProblemDetailForBlankTitle() throws Exception {
        mockMvc.perform(post("/api/v1/books")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"isbn":"9788533302273","title":"","categoryId":1,"authorIds":[10]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")));
    }

    @Test
    @WithMockUser
    void shouldReturn400ForEmptyAuthorIds() throws Exception {
        mockMvc.perform(post("/api/v1/books")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"isbn":"9788533302273","title":"Dom Casmurro","categoryId":1,"authorIds":[]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")));
    }

    @Test
    @WithMockUser
    void shouldReturn400ForNullAuthorIdInsideTheSet() throws Exception {
        // authorIds:[null] goes through Jackson (Set<Long> accepts a null
        // element at runtime), and without this validation it would break
        // further down the line with a 500 instead of a clean 400
        mockMvc.perform(post("/api/v1/books")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"isbn":"9788533302273","title":"Dom Casmurro","categoryId":1,"authorIds":[null]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")));
    }

    @Test
    @WithMockUser
    void shouldReturn422ForNonExistentCategoryOrAuthor() throws Exception {
        given(registerBookUseCase.register(any()))
                .willThrow(new BusinessRuleException("categoria não encontrada: 999999"));

        mockMvc.perform(post("/api/v1/books")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"isbn":"9788533302273","title":"Dom Casmurro","categoryId":999999,"authorIds":[10]}
                                """))
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")))
                .andExpect(jsonPath("$.detail").value("categoria não encontrada: 999999"));
    }

    @Test
    @WithMockUser
    void shouldReturn422ForDuplicateIsbn() throws Exception {
        given(registerBookUseCase.register(any()))
                .willThrow(new BusinessRuleException("já existe um livro com o ISBN '9788533302273'"));

        mockMvc.perform(post("/api/v1/books")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"isbn":"9788533302273","title":"Dom Casmurro","categoryId":1,"authorIds":[10]}
                                """))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    @WithMockUser
    void shouldFindExistingBookById() throws Exception {
        given(getBookUseCase.findById(eq(1L))).willReturn(Optional.of(sampleBook()));

        mockMvc.perform(get("/api/v1/books/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Dom Casmurro"));
    }

    @Test
    @WithMockUser
    void shouldReturn404ForNonExistentId() throws Exception {
        given(getBookUseCase.findById(eq(99L))).willReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/books/99"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")));
    }
}
