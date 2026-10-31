package com.riverfount.booksmanager.catalog.application.service;

import com.riverfount.booksmanager.catalog.application.port.in.BookLookupResult;
import com.riverfount.booksmanager.catalog.application.port.in.BookLookupResult.SuggestedAuthor;
import com.riverfount.booksmanager.catalog.application.port.in.BookLookupResult.Suggestion;
import com.riverfount.booksmanager.catalog.application.port.in.LookupBookByIsbnUseCase;
import com.riverfount.booksmanager.catalog.application.port.out.AuthorRepository;
import com.riverfount.booksmanager.catalog.application.port.out.BookLookup;
import com.riverfount.booksmanager.catalog.application.port.out.BookRepository;
import com.riverfount.booksmanager.catalog.domain.Isbn;
import com.riverfount.booksmanager.catalog.domain.TitleCase;
import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import com.riverfount.booksmanager.shared.domain.InvalidQueryException;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Suggests the data of a book from its ISBN (RF06, RNF19). It validates the ISBN first (the domain's {@link Isbn}),
 * then looks in the collection: an ISBN already registered is reported with the id of its book and nothing is asked
 * outside. Otherwise it asks the {@link BookLookup} port, which never throws, and marks the suggested authors that
 * the catalog already has (active, same name ignoring case) so the front does not register them twice. Without an
 * answer it still answers, as "not found". Read-only: it registers nothing.
 */
@Service
@Transactional(readOnly = true)
class BookLookupService implements LookupBookByIsbnUseCase {

    private final BookRepository bookRepository;
    private final AuthorRepository authorRepository;
    private final BookLookup bookLookup;

    BookLookupService(BookRepository bookRepository, AuthorRepository authorRepository, BookLookup bookLookup) {
        this.bookRepository = bookRepository;
        this.authorRepository = authorRepository;
        this.bookLookup = bookLookup;
    }

    @Override
    public BookLookupResult lookup(String rawIsbn) {
        var isbn = parse(rawIsbn);
        var registered = bookRepository.findIdByIsbn(isbn);
        if (registered.isPresent()) {
            return BookLookupResult.alreadyRegistered(isbn.value(), registered.get());
        }
        return bookLookup.findByIsbn(isbn)
                .map(info -> {
                    var authorNames = info.authors().stream().map(TitleCase::normalize).toList();
                    var existing = existingAuthorsByName(authorNames);
                    var authors = authorNames.stream()
                            .map(name -> new SuggestedAuthor(name, existing.get(name.toLowerCase(Locale.ROOT))))
                            .toList();
                    return BookLookupResult.found(isbn.value(), new Suggestion(
                            TitleCase.normalize(info.title()), info.publisher(), info.publicationYear(), authors));
                })
                .orElseGet(() -> BookLookupResult.notFound(isbn.value()));
    }

    private static Isbn parse(String rawIsbn) {
        try {
            return new Isbn(rawIsbn);
        } catch (BusinessRuleException e) {
            throw new InvalidQueryException(e.getMessage());
        }
    }

    /** The id of each active author of the catalog that has one of the names, by lower-cased name. */
    private Map<String, Long> existingAuthorsByName(java.util.List<String> names) {
        Map<String, Long> byName = new HashMap<>();
        if (names.isEmpty()) {
            return byName;
        }
        for (var author : authorRepository.findActiveByNames(new LinkedHashSet<>(names))) {
            byName.putIfAbsent(author.getName().toLowerCase(Locale.ROOT), author.getId());
        }
        return byName;
    }
}
