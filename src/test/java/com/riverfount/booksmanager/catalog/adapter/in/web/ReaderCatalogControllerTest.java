package com.riverfount.booksmanager.catalog.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.riverfount.booksmanager.WithAdmin;
import com.riverfount.booksmanager.WithOperator;
import com.riverfount.booksmanager.catalog.application.port.in.BookWithAvailability;
import com.riverfount.booksmanager.catalog.application.port.in.GetBookUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.ListAuthorsQuery;
import com.riverfount.booksmanager.catalog.application.port.in.ListAuthorsUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.ListBooksQuery;
import com.riverfount.booksmanager.catalog.application.port.in.ListBooksUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.ListCategoriesQuery;
import com.riverfount.booksmanager.catalog.application.port.in.ListCategoriesUseCase;
import com.riverfount.booksmanager.catalog.domain.Author;
import com.riverfount.booksmanager.catalog.domain.Book;
import com.riverfount.booksmanager.catalog.domain.Category;
import com.riverfount.booksmanager.catalog.domain.Isbn;
import com.riverfount.booksmanager.security.configuration.MethodSecurityConfiguration;
import com.riverfount.booksmanager.shared.adapter.in.web.GlobalExceptionHandler;
import com.riverfount.booksmanager.shared.domain.PageResult;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * The catalog as the reader sees it (Phase 6): the active books with how many copies are available, the active
 * authors and the active categories, with no internal data and no way to ask for the inactive ones.
 */
@WebMvcTest(ReaderCatalogController.class)
@Import({GlobalExceptionHandler.class, MethodSecurityConfiguration.class})
class ReaderCatalogControllerTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-10T00:00:00Z"), ZoneOffset.UTC);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ListBooksUseCase listBooksUseCase;

    @MockitoBean
    private GetBookUseCase getBookUseCase;

    @MockitoBean
    private ListAuthorsUseCase listAuthorsUseCase;

    @MockitoBean
    private ListCategoriesUseCase listCategoriesUseCase;

    private static RequestPostProcessor reader() {
        return jwt().jwt(token -> token.subject("5").claim("readerId", 42L))
                .authorities(new SimpleGrantedAuthority("ROLE_READER"));
    }

    private static BookWithAvailability book(long id, boolean active) {
        var book = Book.reconstitute(id, new Isbn("9788533302273"), "Dom Casmurro", "Garnier", 1899, 3L, Set.of(7L),
                active);
        return new BookWithAvailability(book, 2, 5, Category.reconstitute(3L, "Romance", true),
                List.of(Author.reconstitute(7L, "Machado de Assis", true)));
    }

    // books

    @Test
    void shouldListTheBooksWithTheAvailabilityAndWithoutInternalData() throws Exception {
        given(listBooksUseCase.list(any())).willReturn(new PageResult<>(List.of(book(1L, true)), 0, 20, 1));

        var body = mockMvc.perform(get("/api/v1/me/books").with(reader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(1))
                .andExpect(jsonPath("$.items[0].isbn").value("9788533302273"))
                .andExpect(jsonPath("$.items[0].title").value("Dom Casmurro"))
                .andExpect(jsonPath("$.items[0].publisher").value("Garnier"))
                .andExpect(jsonPath("$.items[0].publicationYear").value(1899))
                .andExpect(jsonPath("$.items[0].availableCopies").value(2))
                .andExpect(jsonPath("$.items[0].category.name").value("Romance"))
                .andExpect(jsonPath("$.items[0].authors[0].name").value("Machado de Assis"))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andReturn().getResponse().getContentAsString();

        // exactly what the reader may see: nothing about the copies (their number in all, their codes) or the state
        java.util.Map<String, Object> book = JsonPath.read(body, "$.items[0]");
        assertThat(book.keySet()).containsExactlyInAnyOrder("id", "isbn", "title", "publisher", "publicationYear",
                "availableCopies", "category", "authors");
    }

    @Test
    void shouldAlwaysListOnlyActiveBooksAndPassTheSearchFilters() throws Exception {
        given(listBooksUseCase.list(any())).willReturn(new PageResult<>(List.of(), 0, 10, 0));

        // "active=false" is not a parameter of the reader: it is ignored
        mockMvc.perform(get("/api/v1/me/books").with(reader()).param("title", "casmurro").param("author", "machado")
                        .param("isbn", "978").param("categoryId", "3").param("active", "false")
                        .param("page", "1").param("size", "10").param("sort", "title,desc"))
                .andExpect(status().isOk());

        var captor = ArgumentCaptor.forClass(ListBooksQuery.class);
        org.mockito.Mockito.verify(listBooksUseCase).list(captor.capture());
        var query = captor.getValue();
        assertThat(query.active()).isTrue();
        assertThat(query.title()).isEqualTo("casmurro");
        assertThat(query.author()).isEqualTo("machado");
        assertThat(query.isbn()).isEqualTo("978");
        assertThat(query.categoryId()).isEqualTo(3L);
        assertThat(query.page().page()).isEqualTo(1);
        assertThat(query.page().size()).isEqualTo(10);
    }

    @Test
    void shouldRefuseAPageBiggerThan100AndAnUnknownSortWith400() throws Exception {
        mockMvc.perform(get("/api/v1/me/books").with(reader()).param("size", "101"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/me/books").with(reader()).param("sort", "password,asc"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(getBookUseCase);
    }

    @Test
    void shouldShowAnActiveBookById() throws Exception {
        given(getBookUseCase.findById(1L)).willReturn(Optional.of(book(1L, true)));

        mockMvc.perform(get("/api/v1/me/books/1").with(reader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Dom Casmurro"))
                .andExpect(jsonPath("$.availableCopies").value(2))
                .andExpect(jsonPath("$.totalCopies").doesNotExist())
                .andExpect(jsonPath("$.active").doesNotExist());
    }

    @Test
    void anInactiveOrUnknownBookShouldBeThe404Both() throws Exception {
        given(getBookUseCase.findById(2L)).willReturn(Optional.of(book(2L, false)));
        given(getBookUseCase.findById(3L)).willReturn(Optional.empty());

        var inactive = mockMvc.perform(get("/api/v1/me/books/2").with(reader()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andReturn().getResponse().getContentAsString();
        var unknown = mockMvc.perform(get("/api/v1/me/books/3").with(reader()))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();

        // the answers differ only in the id of the request, so the reader cannot tell the two cases apart
        assertThat(detailWithoutDigits(inactive)).isEqualTo(detailWithoutDigits(unknown));
    }

    private String detailWithoutDigits(String problem) throws Exception {
        String detail = JsonPath.read(problem, "$.detail");
        return detail.replaceAll("\\d+", "N");
    }

    // authors and categories

    @Test
    void shouldListTheActiveAuthorsByName() throws Exception {
        given(listAuthorsUseCase.list(any())).willReturn(new PageResult<>(
                List.of(Author.reconstitute(7L, "Machado de Assis", true)), 0, 20, 1));

        mockMvc.perform(get("/api/v1/me/authors").with(reader()).param("name", "machado").param("active", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(7))
                .andExpect(jsonPath("$.items[0].name").value("Machado de Assis"))
                .andExpect(jsonPath("$.items[0].active").doesNotExist());

        var captor = ArgumentCaptor.forClass(ListAuthorsQuery.class);
        org.mockito.Mockito.verify(listAuthorsUseCase).list(captor.capture());
        assertThat(captor.getValue().active()).isTrue();
        assertThat(captor.getValue().name()).isEqualTo("machado");
    }

    @Test
    void shouldListTheActiveCategoriesByName() throws Exception {
        given(listCategoriesUseCase.list(any())).willReturn(new PageResult<>(
                List.of(Category.reconstitute(3L, "Romance", true)), 0, 20, 1));

        mockMvc.perform(get("/api/v1/me/categories").with(reader()).param("name", "rom").param("active", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(3))
                .andExpect(jsonPath("$.items[0].name").value("Romance"))
                .andExpect(jsonPath("$.items[0].active").doesNotExist());

        var captor = ArgumentCaptor.forClass(ListCategoriesQuery.class);
        org.mockito.Mockito.verify(listCategoriesUseCase).list(captor.capture());
        assertThat(captor.getValue().active()).isTrue();
        assertThat(captor.getValue().name()).isEqualTo("rom");
    }

    @Test
    void shouldRefuseAPageBiggerThan100OnAuthorsAndCategories() throws Exception {
        mockMvc.perform(get("/api/v1/me/authors").with(reader()).param("size", "101"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/me/categories").with(reader()).param("size", "101"))
                .andExpect(status().isBadRequest());
    }

    // who may call

    @Test
    @WithAdmin
    void anAdministratorMayNotUseTheRoutesOfTheReader() throws Exception {
        for (var path : new String[] {"/api/v1/me/books", "/api/v1/me/books/1", "/api/v1/me/authors",
                "/api/v1/me/categories"}) {
            mockMvc.perform(get(path)).andExpect(status().isForbidden());
        }
    }

    @Test
    @WithOperator
    void anOperatorMayNotUseTheRoutesOfTheReader() throws Exception {
        mockMvc.perform(get("/api/v1/me/books")).andExpect(status().isForbidden());
        verifyNoInteractions(listBooksUseCase);
    }

    @Test
    void anonymousIs401() throws Exception {
        mockMvc.perform(get("/api/v1/me/books")).andExpect(status().isUnauthorized());
    }
}
