package com.riverfount.booksmanager.catalog.adapter.in.web;

import static org.mockito.Mockito.never;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
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
import com.riverfount.booksmanager.catalog.application.port.in.BookWithAvailability;
import com.riverfount.booksmanager.catalog.application.port.in.DeactivateBookUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.ReactivateBookUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.GetBookUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.ListBooksQuery;
import com.riverfount.booksmanager.catalog.application.port.in.BookLookupResult;
import com.riverfount.booksmanager.catalog.application.port.in.ListBooksUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.LookupBookByIsbnUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.RegisterBookUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.UpdateBookCommand;
import com.riverfount.booksmanager.catalog.application.port.in.UpdateBookUseCase;
import com.riverfount.booksmanager.catalog.domain.Author;
import com.riverfount.booksmanager.catalog.domain.Book;
import com.riverfount.booksmanager.catalog.domain.Category;
import com.riverfount.booksmanager.catalog.domain.Isbn;
import com.riverfount.booksmanager.shared.adapter.in.web.GlobalExceptionHandler;
import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import com.riverfount.booksmanager.shared.domain.PageResult;
import com.riverfount.booksmanager.shared.domain.ResourceNotFoundException;
import com.riverfount.booksmanager.shared.domain.SortDirection;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(BookController.class)
@Import({GlobalExceptionHandler.class, MethodSecurityConfiguration.class})
class BookControllerTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-10-10T00:00:00Z"), ZoneOffset.UTC);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RegisterBookUseCase registerBookUseCase;

    @MockitoBean
    private GetBookUseCase getBookUseCase;

    @MockitoBean
    private ListBooksUseCase listBooksUseCase;

    @MockitoBean
    private UpdateBookUseCase updateBookUseCase;

    @MockitoBean
    private DeactivateBookUseCase deactivateBookUseCase;

    @MockitoBean
    private ReactivateBookUseCase reactivateBookUseCase;

    @MockitoBean
    private LookupBookByIsbnUseCase lookupBookByIsbnUseCase;

    private BookWithAvailability sampleBook() {
        return withNames(plainBook(), 2, 5);
    }

    private static BookWithAvailability withNames(Book book, long available, long total) {
        return new BookWithAvailability(book, available, total, Category.reconstitute(1L, "Romance", true),
                List.of(Author.reconstitute(10L, "Machado de Assis", true)));
    }

    private Book plainBook() {
        return Book.create(new Isbn("9788533302273"), "Dom Casmurro", "Editora X", 1899, 1L, Set.of(10L),
                FIXED_CLOCK);
    }

    @Test
    @WithOperator
    void shouldRegisterBookAndReturn201WithLocation() throws Exception {
        given(registerBookUseCase.register(any())).willReturn(withNames(plainBook(), 0, 0));

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
                .andExpect(jsonPath("$.authorIds[0]").value(10))
                .andExpect(jsonPath("$.availableCopies").value(0));
    }

    @Test
    @WithOperator
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
    @WithOperator
    void shouldReturn400ForPublisherLongerThan255() throws Exception {
        var publisher = "e".repeat(256);

        mockMvc.perform(post("/api/v1/books")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isbn\":\"9788533302273\",\"title\":\"Dom Casmurro\",\"publisher\":\"" + publisher
                                + "\",\"categoryId\":1,\"authorIds\":[10]}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")));

        then(registerBookUseCase).shouldHaveNoInteractions();
    }

    @Test
    @WithOperator
    void shouldReturn400ForPublicationYearBelow1450() throws Exception {
        mockMvc.perform(post("/api/v1/books")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"isbn":"9788533302273","title":"Dom Casmurro","publicationYear":1449,
                                 "categoryId":1,"authorIds":[10]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")));

        then(registerBookUseCase).shouldHaveNoInteractions();
    }

    @Test
    @WithOperator
    void shouldReturn400WhenUpdatingWithPublisherLongerThan255() throws Exception {
        mockMvc.perform(patch("/api/v1/books/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"publisher\":\"" + "e".repeat(256) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")));

        then(updateBookUseCase).shouldHaveNoInteractions();
    }

    @Test
    @WithOperator
    void shouldReturn400WhenUpdatingWithPublicationYearBelow1450() throws Exception {
        mockMvc.perform(patch("/api/v1/books/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"publicationYear\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")));

        then(updateBookUseCase).shouldHaveNoInteractions();
    }

    @Test
    @WithOperator
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
    @WithOperator
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
    @WithOperator
    void shouldReturn422ForNonExistentCategoryOrAuthor() throws Exception {
        given(registerBookUseCase.register(any()))
                .willThrow(new BusinessRuleException("categoria não encontrada: 999999"));

        mockMvc.perform(post("/api/v1/books")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"isbn":"9788533302273","title":"Dom Casmurro","publisher":"Editora X","publicationYear":1899,"categoryId":999999,"authorIds":[10]}
                                """))
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")))
                .andExpect(jsonPath("$.detail").value("categoria não encontrada: 999999"));
    }

    @Test
    @WithOperator
    void shouldReturn422ForDuplicateIsbn() throws Exception {
        given(registerBookUseCase.register(any()))
                .willThrow(new BusinessRuleException("já existe um livro com o ISBN '9788533302273'"));

        mockMvc.perform(post("/api/v1/books")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"isbn":"9788533302273","title":"Dom Casmurro","publisher":"Editora X","publicationYear":1899,"categoryId":1,"authorIds":[10]}
                                """))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    @WithOperator
    void shouldFindExistingBookById() throws Exception {
        given(getBookUseCase.findById(eq(1L))).willReturn(Optional.of(sampleBook()));

        mockMvc.perform(get("/api/v1/books/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Dom Casmurro"))
                .andExpect(jsonPath("$.availableCopies").value(2));
    }

    @Test
    @WithOperator
    void shouldReturn404ForNonExistentId() throws Exception {
        given(getBookUseCase.findById(eq(99L))).willReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/books/99"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")));
    }

    @Test
    @WithOperator
    void shouldListBooksWithPaginationMetadata() throws Exception {
        given(listBooksUseCase.list(any())).willReturn(new PageResult<>(List.of(new BookWithAvailability(
                Book.reconstitute(1L, new Isbn("9788533302273"), "Dom Casmurro", "Garnier", 1899, 3L, Set.of(7L),
                        true), 5, 8, Category.reconstitute(3L, "Romance", true),
                List.of(Author.reconstitute(7L, "Machado de Assis", true)))), 0, 1, 2));

        mockMvc.perform(get("/api/v1/books").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(1))
                .andExpect(jsonPath("$.items[0].isbn").value("9788533302273"))
                .andExpect(jsonPath("$.items[0].title").value("Dom Casmurro"))
                .andExpect(jsonPath("$.items[0].categoryId").value(3))
                .andExpect(jsonPath("$.items[0].authorIds[0]").value(7))
                .andExpect(jsonPath("$.items[0].active").value(true))
                .andExpect(jsonPath("$.items[0].availableCopies").value(5))
                .andExpect(jsonPath("$.items[0].totalCopies").value(8))
                .andExpect(jsonPath("$.items[0].category.id").value(3))
                .andExpect(jsonPath("$.items[0].category.name").value("Romance"))
                .andExpect(jsonPath("$.items[0].authors[0].id").value(7))
                .andExpect(jsonPath("$.items[0].authors[0].name").value("Machado de Assis"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    @WithOperator
    void shouldListOnlyActiveBooksSortedByTitleByDefault() throws Exception {
        given(listBooksUseCase.list(any())).willReturn(new PageResult<>(List.of(), 0, 20, 0));

        mockMvc.perform(get("/api/v1/books")).andExpect(status().isOk());

        var query = capturedQuery();
        assertThat(query.active()).isTrue();
        assertThat(query.page().page()).isZero();
        assertThat(query.page().size()).isEqualTo(20);
        assertThat(query.page().sortBy()).isEqualTo("title");
        assertThat(query.page().direction()).isEqualTo(SortDirection.ASC);
    }

    @Test
    @WithOperator
    void shouldPassActiveFlagPaginationAndSortToTheUseCase() throws Exception {
        given(listBooksUseCase.list(any())).willReturn(new PageResult<>(List.of(), 2, 10, 0));

        mockMvc.perform(get("/api/v1/books")
                        .param("active", "false")
                        .param("page", "2")
                        .param("size", "10")
                        .param("sort", "publicationYear,desc"))
                .andExpect(status().isOk());

        var query = capturedQuery();
        assertThat(query.active()).isFalse();
        assertThat(query.page().page()).isEqualTo(2);
        assertThat(query.page().size()).isEqualTo(10);
        assertThat(query.page().sortBy()).isEqualTo("publicationYear");
        assertThat(query.page().direction()).isEqualTo(SortDirection.DESC);
    }

    @Test
    @WithOperator
    void shouldReturn400ForPageSizeAbove100() throws Exception {
        mockMvc.perform(get("/api/v1/books").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")))
                .andExpect(jsonPath("$.detail").value(containsString("100")));
    }

    @Test
    @WithOperator
    void shouldReturn400ForNegativePage() throws Exception {
        mockMvc.perform(get("/api/v1/books").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")));
    }

    @Test
    @WithOperator
    void shouldReturn400ForUnknownSortField() throws Exception {
        mockMvc.perform(get("/api/v1/books").param("sort", "publisher,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")))
                .andExpect(jsonPath("$.detail").value(containsString("publisher")));
    }

    @Test
    @WithOperator
    void shouldReturn400ForInvalidActiveValue() throws Exception {
        mockMvc.perform(get("/api/v1/books").param("active", "maybe"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")));
    }

    @Test
    @WithOperator
    void shouldUpdateBookAndReturn200() throws Exception {
        given(updateBookUseCase.update(any())).willReturn(sampleBook());

        mockMvc.perform(patch("/api/v1/books/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Dom Casmurro","publisher":"Editora X","publicationYear":1899,
                                 "categoryId":1,"authorIds":[10]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Dom Casmurro"))
                .andExpect(jsonPath("$.isbn").value("9788533302273"));

        then(updateBookUseCase).should().update(new UpdateBookCommand(1L, "Dom Casmurro", "Editora X", 1899,
                1L, Set.of(10L)));
    }

    @Test
    @WithOperator
    void shouldSendNullsForOmittedFieldsOnUpdate() throws Exception {
        given(updateBookUseCase.update(any())).willReturn(sampleBook());

        mockMvc.perform(patch("/api/v1/books/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Novo título\"}"))
                .andExpect(status().isOk());

        then(updateBookUseCase).should().update(new UpdateBookCommand(1L, "Novo título", null, null, null,
                null));
    }

    @Test
    @WithOperator
    void shouldIgnoreIsbnSentOnUpdate() throws Exception {
        given(updateBookUseCase.update(any())).willReturn(sampleBook());

        mockMvc.perform(patch("/api/v1/books/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isbn\":\"9788535914849\",\"title\":\"Novo título\"}"))
                .andExpect(status().isOk());

        then(updateBookUseCase).should().update(new UpdateBookCommand(1L, "Novo título", null, null, null,
                null));
    }

    @Test
    @WithOperator
    void shouldReturn400WhenUpdatingWithBlankTitle() throws Exception {
        mockMvc.perform(patch("/api/v1/books/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")));

        then(updateBookUseCase).shouldHaveNoInteractions();
    }

    @Test
    @WithOperator
    void shouldReturn400WhenUpdatingWithEmptyAuthorIds() throws Exception {
        mockMvc.perform(patch("/api/v1/books/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"authorIds\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")));

        then(updateBookUseCase).shouldHaveNoInteractions();
    }

    @Test
    @WithOperator
    void shouldReturn404WhenUpdatingNonExistentBook() throws Exception {
        given(updateBookUseCase.update(any())).willThrow(new ResourceNotFoundException("livro não encontrado: 99"));

        mockMvc.perform(patch("/api/v1/books/99")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Novo título\"}"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")));
    }

    @Test
    @WithOperator
    void shouldReturn422WhenUpdatingWithInactiveCategory() throws Exception {
        given(updateBookUseCase.update(any())).willThrow(new BusinessRuleException("categoria inativa: 5"));

        mockMvc.perform(patch("/api/v1/books/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":5}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")))
                .andExpect(jsonPath("$.detail").value(containsString("categoria inativa")));
    }

    @Test
    @WithOperator
    void shouldDeactivateBookAndReturn204WithoutBody() throws Exception {
        mockMvc.perform(delete("/api/v1/books/1").with(csrf()))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        then(deactivateBookUseCase).should().deactivate(1L);
    }

    @Test
    @WithOperator
    void shouldReturn404WhenDeactivatingNonExistentBook() throws Exception {
        willThrow(new ResourceNotFoundException("livro não encontrado: 99"))
                .given(deactivateBookUseCase).deactivate(99L);

        mockMvc.perform(delete("/api/v1/books/99").with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")));
    }

    @Test
    @WithOperator
    void shouldUpdateBookReturningTheAvailableCopies() throws Exception {
        given(updateBookUseCase.update(any())).willReturn(sampleBook());

        mockMvc.perform(patch("/api/v1/books/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Dom Casmurro\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableCopies").value(2));
    }

    @Test
    @WithOperator
    void shouldNotFilterTheListingByDefault() throws Exception {
        given(listBooksUseCase.list(any())).willReturn(new PageResult<>(List.of(), 0, 20, 0));

        mockMvc.perform(get("/api/v1/books")).andExpect(status().isOk());

        var query = capturedQuery();
        assertThat(query.title()).isNull();
        assertThat(query.author()).isNull();
        assertThat(query.isbn()).isNull();
        assertThat(query.categoryId()).isNull();
    }

    @Test
    @WithOperator
    void shouldPassTheSearchFiltersToTheUseCase() throws Exception {
        given(listBooksUseCase.list(any())).willReturn(new PageResult<>(List.of(), 0, 20, 0));

        mockMvc.perform(get("/api/v1/books")
                        .param("title", "casmurro")
                        .param("author", "machado")
                        .param("isbn", "978-85-3330")
                        .param("categoryId", "3")
                        .param("sort", "title,desc"))
                .andExpect(status().isOk());

        var query = capturedQuery();
        assertThat(query.title()).isEqualTo("casmurro");
        assertThat(query.author()).isEqualTo("machado");
        assertThat(query.isbn()).isEqualTo("978-85-3330");
        assertThat(query.categoryId()).isEqualTo(3L);
        assertThat(query.active()).isTrue();
        assertThat(query.page().sortBy()).isEqualTo("title");
        assertThat(query.page().direction()).isEqualTo(SortDirection.DESC);
    }

    @Test
    @WithOperator
    void shouldReturn400ForNonNumericCategoryId() throws Exception {
        mockMvc.perform(get("/api/v1/books").param("categoryId", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")));

        then(listBooksUseCase).shouldHaveNoInteractions();
    }

    private ListBooksQuery capturedQuery() {
        var captor = ArgumentCaptor.forClass(ListBooksQuery.class);
        then(listBooksUseCase).should().list(captor.capture());
        return captor.getValue();
    }

    // authorization by profile (RNF08): the whole controller is for an administrator or an operator

    @Test
    @WithMockUser(roles = "USER")
    void shouldRefuseAnAuthenticatedUserWithoutAnAllowedProfileWith403() throws Exception {
        mockMvc.perform(get("/api/v1/books"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.detail").value("Você não tem permissão para acessar este recurso."));
        mockMvc.perform(delete("/api/v1/books/1").with(csrf())).andExpect(status().isForbidden());

        then(deactivateBookUseCase).should(never()).deactivate(any());
    }

    @Test
    @WithMockUser(roles = {})
    void shouldRefuseAnAuthenticatedUserWithNoProfileAtAllWith403() throws Exception {
        mockMvc.perform(delete("/api/v1/books/1").with(csrf())).andExpect(status().isForbidden());

        then(deactivateBookUseCase).should(never()).deactivate(any());
    }

    @Test
    @WithAdmin
    void anAdministratorMayCallTheRoutesToo() throws Exception {
        mockMvc.perform(delete("/api/v1/books/1").with(csrf())).andExpect(status().isNoContent());
    }

    @Test
    @WithOperator
    void anOperatorMayCallTheRoutes() throws Exception {
        mockMvc.perform(delete("/api/v1/books/1").with(csrf())).andExpect(status().isNoContent());
    }

    // the lookup of an ISBN (RF06)

    @Test
    @WithOperator
    void shouldSuggestTheDataOfAnIsbnAndIdentifyTheKnownAuthors() throws Exception {
        given(lookupBookByIsbnUseCase.lookup("978-85-333-0227-3")).willReturn(BookLookupResult.found("9788533302273",
                new BookLookupResult.Suggestion("Dom Casmurro", "Garnier", 1899,
                        List.of(new BookLookupResult.SuggestedAuthor("Machado de Assis", 3L),
                                new BookLookupResult.SuggestedAuthor("Outro", null)))));

        mockMvc.perform(get("/api/v1/books/lookup").param("isbn", "978-85-333-0227-3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isbn").value("9788533302273"))
                .andExpect(jsonPath("$.outcome").value("FOUND"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.suggestion.title").value("Dom Casmurro"))
                .andExpect(jsonPath("$.suggestion.publisher").value("Garnier"))
                .andExpect(jsonPath("$.suggestion.publicationYear").value(1899))
                .andExpect(jsonPath("$.suggestion.authors[0].name").value("Machado de Assis"))
                .andExpect(jsonPath("$.suggestion.authors[0].existingAuthorId").value(3))
                .andExpect(jsonPath("$.suggestion.authors[1].existingAuthorId").doesNotExist())
                .andExpect(jsonPath("$.registeredBookId").doesNotExist());
    }

    @Test
    @WithAdmin
    void shouldSignalAnIsbnThatIsAlreadyRegistered() throws Exception {
        given(lookupBookByIsbnUseCase.lookup("9788533302273"))
                .willReturn(BookLookupResult.alreadyRegistered("9788533302273", 7L));

        mockMvc.perform(get("/api/v1/books/lookup").param("isbn", "9788533302273"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("ALREADY_REGISTERED"))
                .andExpect(jsonPath("$.registeredBookId").value(7))
                .andExpect(jsonPath("$.message").value(containsString("Já existe")))
                .andExpect(jsonPath("$.suggestion").doesNotExist());
    }

    @Test
    @WithOperator
    void shouldAnswer200WithAReasonWhenNothingCameBack() throws Exception {
        given(lookupBookByIsbnUseCase.lookup("9788533302273")).willReturn(BookLookupResult.notFound("9788533302273"));

        mockMvc.perform(get("/api/v1/books/lookup").param("isbn", "9788533302273"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value(containsString("manualmente")))
                .andExpect(jsonPath("$.suggestion").doesNotExist());
    }

    @Test
    @WithOperator
    void shouldAnswer400ForAnInvalidOrMissingIsbn() throws Exception {
        given(lookupBookByIsbnUseCase.lookup("123"))
                .willThrow(new com.riverfount.booksmanager.shared.domain.InvalidQueryException("ISBN deve ter 10 ou 13 dígitos"));
        given(lookupBookByIsbnUseCase.lookup(null))
                .willThrow(new com.riverfount.booksmanager.shared.domain.InvalidQueryException("o ISBN é obrigatório"));

        mockMvc.perform(get("/api/v1/books/lookup").param("isbn", "123"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.detail").value("ISBN deve ter 10 ou 13 dígitos"));
        mockMvc.perform(get("/api/v1/books/lookup")).andExpect(status().isBadRequest());
    }

    @Test
    void shouldRequireAuthenticationForTheLookup() throws Exception {
        mockMvc.perform(get("/api/v1/books/lookup").param("isbn", "9788533302273"))
                .andExpect(status().isUnauthorized());
    }

    // reactivation (RN14): only the administrator

    @Test
    @WithAdmin
    void anAdministratorShouldReactivateAndGetTheResource() throws Exception {
        given(reactivateBookUseCase.reactivate(1L)).willReturn(sampleBook());

        mockMvc.perform(post("/api/v1/books/1/reactivate").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.active").value(true))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.title").exists());

        then(reactivateBookUseCase).should().reactivate(1L);
    }

    @Test
    @WithOperator
    void anOperatorShouldNotReactivateAndGet403() throws Exception {
        mockMvc.perform(post("/api/v1/books/1/reactivate").with(csrf()))
                .andExpect(status().isForbidden());

        then(reactivateBookUseCase).shouldHaveNoInteractions();
    }

    @Test
    void withoutATokenReactivatingShouldBe401() throws Exception {
        mockMvc.perform(post("/api/v1/books/1/reactivate").with(csrf()))
                .andExpect(status().isUnauthorized());

        then(reactivateBookUseCase).shouldHaveNoInteractions();
    }

    @Test
    @WithAdmin
    void reactivatingSomethingThatDoesNotExistShouldBe404() throws Exception {
        willThrow(new ResourceNotFoundException("livro não encontrado: 99")).given(reactivateBookUseCase).reactivate(99L);

        mockMvc.perform(post("/api/v1/books/99/reactivate").with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")));
    }

    @Test
    @WithAdmin
    void aRefusedReactivationShouldBe422WithTheReason() throws Exception {
        willThrow(new com.riverfount.booksmanager.shared.domain.BusinessRuleException("o livro não pode ser reativado (categoria inativa: Romance): reative antes"))
                .given(reactivateBookUseCase).reactivate(1L);

        mockMvc.perform(post("/api/v1/books/1/reactivate").with(csrf()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.detail").value("o livro não pode ser reativado (categoria inativa: Romance): reative antes"));
    }

    // every book has a publisher and a publication year (RF01)

    @Test
    @WithOperator
    void shouldReturn400WhenRegisteringWithoutPublisher() throws Exception {
        mockMvc.perform(post("/api/v1/books")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"isbn":"9788533302273","title":"Dom Casmurro","publicationYear":1899,
                                 "categoryId":1,"authorIds":[10]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")));

        then(registerBookUseCase).shouldHaveNoInteractions();
    }

    @Test
    @WithOperator
    void shouldReturn400WhenRegisteringWithABlankPublisher() throws Exception {
        mockMvc.perform(post("/api/v1/books")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"isbn":"9788533302273","title":"Dom Casmurro","publisher":"   ",
                                 "publicationYear":1899,"categoryId":1,"authorIds":[10]}
                                """))
                .andExpect(status().isBadRequest());

        then(registerBookUseCase).shouldHaveNoInteractions();
    }

    @Test
    @WithOperator
    void shouldReturn400WhenRegisteringWithoutPublicationYear() throws Exception {
        mockMvc.perform(post("/api/v1/books")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"isbn":"9788533302273","title":"Dom Casmurro","publisher":"Editora X",
                                 "categoryId":1,"authorIds":[10]}
                                """))
                .andExpect(status().isBadRequest());

        then(registerBookUseCase).shouldHaveNoInteractions();
    }

    @Test
    @WithOperator
    void shouldReturn400WhenUpdatingWithABlankPublisher() throws Exception {
        mockMvc.perform(patch("/api/v1/books/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"publisher\":\"  \"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/v1/books/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"publisher\":\"\"}"))
                .andExpect(status().isBadRequest());

        then(updateBookUseCase).shouldHaveNoInteractions();
    }
}
