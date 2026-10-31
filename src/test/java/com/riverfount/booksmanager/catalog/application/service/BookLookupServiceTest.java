package com.riverfount.booksmanager.catalog.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import com.riverfount.booksmanager.catalog.application.port.in.BookLookupResult.Outcome;
import com.riverfount.booksmanager.catalog.application.port.out.BookInfo;
import com.riverfount.booksmanager.catalog.application.port.out.BookLookup;
import com.riverfount.booksmanager.catalog.domain.Author;
import com.riverfount.booksmanager.catalog.domain.Book;
import com.riverfount.booksmanager.catalog.domain.Isbn;
import com.riverfount.booksmanager.shared.domain.InvalidQueryException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The suggestion of a book from its ISBN (RF06, RNF19): the ISBN is validated before anything is asked outside, an ISBN
 * already in the collection is reported without asking, the suggested authors the catalog already has are identified,
 * and no answer from the outside is still an answer.
 */
class BookLookupServiceTest {

    private static final String DOM_CASMURRO = "9788533302273";

    private InMemoryBookRepository books;
    private InMemoryAuthorRepository authors;
    private BookLookup bookLookup;
    private BookLookupService service;

    @BeforeEach
    void setUp() {
        books = new InMemoryBookRepository();
        authors = new InMemoryAuthorRepository();
        bookLookup = mock(BookLookup.class);
        service = new BookLookupService(books, authors, bookLookup);
    }

    private void sourceKnows(BookInfo info) {
        given(bookLookup.findByIsbn(new Isbn(DOM_CASMURRO))).willReturn(Optional.of(info));
    }

    // a suggestion

    @Test
    void shouldSuggestTheDataOfAKnownIsbn() {
        sourceKnows(new BookInfo("Dom Casmurro", List.of("Machado de Assis"), "Garnier", 1899));

        var result = service.lookup(DOM_CASMURRO);

        assertThat(result.outcome()).isEqualTo(Outcome.FOUND);
        assertThat(result.isbn()).isEqualTo(DOM_CASMURRO);
        assertThat(result.registeredBookId()).isNull();
        assertThat(result.suggestion().title()).isEqualTo("Dom Casmurro");
        assertThat(result.suggestion().publisher()).isEqualTo("Garnier");
        assertThat(result.suggestion().publicationYear()).isEqualTo(1899);
        assertThat(result.suggestion().authors()).singleElement()
                .satisfies(author -> assertThat(author.name()).isEqualTo("Machado de Assis"));
    }

    @Test
    void shouldNormalizeTheIsbnTypedWithHyphens() {
        sourceKnows(new BookInfo("Dom Casmurro", List.of(), null, null));

        var result = service.lookup("978-85-333-0227-3");

        assertThat(result.outcome()).isEqualTo(Outcome.FOUND);
        assertThat(result.isbn()).isEqualTo(DOM_CASMURRO);
    }

    @Test
    void shouldIdentifyTheSuggestedAuthorsTheCatalogAlreadyHasIgnoringCase() {
        var machado = authors.save(Author.create("Machado de Assis"));
        sourceKnows(new BookInfo("Dom Casmurro", List.of("MACHADO DE ASSIS", "Outro Autor"), null, null));

        var suggested = service.lookup(DOM_CASMURRO).suggestion().authors();

        assertThat(suggested).extracting(a -> a.name(), a -> a.existingAuthorId())
                .containsExactly(org.assertj.core.groups.Tuple.tuple("Machado de Assis", machado.getId()),
                        org.assertj.core.groups.Tuple.tuple("Outro Autor", null));
    }

    @Test
    void anInactiveAuthorShouldNotBeOfferedForReuse() {
        var inactive = Author.create("Machado de Assis");
        inactive.deactivate();
        authors.save(inactive);
        sourceKnows(new BookInfo("Dom Casmurro", List.of("Machado de Assis"), null, null));

        assertThat(service.lookup(DOM_CASMURRO).suggestion().authors().get(0).existingAuthorId()).isNull();
    }

    @Test
    void shouldSuggestABookWithoutAuthorsWithoutAskingTheCatalogForThem() {
        sourceKnows(new BookInfo("Dom Casmurro", List.of(), null, null));

        assertThat(service.lookup(DOM_CASMURRO).suggestion().authors()).isEmpty();
    }

    // nothing to suggest

    @Test
    void shouldAnswerNotFoundWhenTheSourceHasNothing() {
        given(bookLookup.findByIsbn(any())).willReturn(Optional.empty());

        var result = service.lookup(DOM_CASMURRO);

        assertThat(result.outcome()).isEqualTo(Outcome.NOT_FOUND);
        assertThat(result.isbn()).isEqualTo(DOM_CASMURRO);
        assertThat(result.suggestion()).isNull();
        assertThat(result.registeredBookId()).isNull();
    }

    // an ISBN already in the collection

    @Test
    void shouldReportAnIsbnAlreadyRegisteredWithoutAskingTheSource() {
        var saved = books.save(Book.create(new Isbn(DOM_CASMURRO), "Dom Casmurro", "Editora X", 1899, 1L, Set.of(10L),
                Clock.fixed(Instant.parse("2026-10-10T00:00:00Z"), ZoneOffset.UTC)));

        var result = service.lookup(DOM_CASMURRO);

        assertThat(result.outcome()).isEqualTo(Outcome.ALREADY_REGISTERED);
        assertThat(result.registeredBookId()).isEqualTo(saved.getId());
        assertThat(result.suggestion()).isNull();
        then(bookLookup).should(never()).findByIsbn(any());
    }

    @Test
    void shouldReportAsRegisteredTheIsbn10OfABookRegisteredWithTheIsbn13() {
        var saved = books.save(Book.create(new Isbn("9788533302273"), "Dom Casmurro", "Editora X", 1899, 1L, Set.of(10L),
                Clock.fixed(Instant.parse("2026-10-10T00:00:00Z"), ZoneOffset.UTC)));

        var result = service.lookup("85-333-0227-4");

        assertThat(result.outcome()).isEqualTo(Outcome.ALREADY_REGISTERED);
        assertThat(result.registeredBookId()).isEqualTo(saved.getId());
        assertThat(result.isbn()).isEqualTo("9788533302273");
        then(bookLookup).should(never()).findByIsbn(any());
    }

    @Test
    void anInactiveBookKeepsItsIsbnSoItIsAlsoReported() {
        var book = Book.create(new Isbn(DOM_CASMURRO), "Dom Casmurro", "Editora X", 1899, 1L, Set.of(10L),
                Clock.fixed(Instant.parse("2026-10-10T00:00:00Z"), ZoneOffset.UTC));
        book.deactivate();
        books.save(book);

        assertThat(service.lookup(DOM_CASMURRO).outcome()).isEqualTo(Outcome.ALREADY_REGISTERED);
    }

    // an invalid ISBN

    @Test
    void shouldRefuseAnInvalidIsbnBeforeAnythingIsAskedOutside() {
        for (var invalid : new String[] {"123", "9788533302274", "abcdefghijklm", "", "  ", null}) {
            assertThatThrownBy(() -> service.lookup(invalid)).as(String.valueOf(invalid))
                    .isInstanceOf(InvalidQueryException.class);
        }
        then(bookLookup).shouldHaveNoInteractions();
    }

    @Test
    void shouldAcceptAnIsbn10Too() {
        given(bookLookup.findByIsbn(new Isbn("080442957X"))).willReturn(Optional.empty());

        assertThat(service.lookup("0-8044-2957-X").outcome()).isEqualTo(Outcome.NOT_FOUND);
    }

    // casing of the source

    @Test
    void shouldRestoreTheCasingOfATitleAndAuthorsThatCameInUppercase() {
        sourceKnows(new BookInfo("O NASCIMENTO DA INTELIGÊNCIA NA CRIANÇA", List.of("JEAN PIAGET"), "Zahar", 1970));

        var suggestion = service.lookup(DOM_CASMURRO).suggestion();

        assertThat(suggestion.title()).isEqualTo("O Nascimento da Inteligência na Criança");
        assertThat(suggestion.authors()).extracting(a -> a.name()).containsExactly("Jean Piaget");
    }

    @Test
    void shouldStillIdentifyAnExistingAuthorWhenTheSourceNameCameInUppercase() {
        var piaget = authors.save(Author.create("Jean Piaget"));
        sourceKnows(new BookInfo("O NASCIMENTO DA INTELIGÊNCIA NA CRIANÇA", List.of("JEAN PIAGET"), "Zahar", 1970));

        var suggested = service.lookup(DOM_CASMURRO).suggestion().authors();

        assertThat(suggested).extracting(a -> a.name(), a -> a.existingAuthorId())
                .containsExactly(org.assertj.core.api.Assertions.tuple("Jean Piaget", piaget.getId()));
    }

    @Test
    void shouldLeaveAMixedCaseSuggestionUntouched() {
        sourceKnows(new BookInfo("Rei Arthur II", List.of("J.R.R. Tolkien"), "Garnier", 1899));

        var suggestion = service.lookup(DOM_CASMURRO).suggestion();

        assertThat(suggestion.title()).isEqualTo("Rei Arthur II");
        assertThat(suggestion.authors()).extracting(a -> a.name()).containsExactly("J.R.R. Tolkien");
    }
}
