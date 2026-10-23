package com.riverfount.booksmanager.catalog.adapter.out.isbn;

import com.riverfount.booksmanager.catalog.application.port.out.BookInfo;
import com.riverfount.booksmanager.catalog.application.port.out.BookLookup;
import com.riverfount.booksmanager.catalog.domain.Isbn;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Looks the book up in Open Library (RF06) with the JDK HTTP client, so no library is added. The answer of the
 * source ({@code /api/books?bibkeys=ISBN:...&jscmd=data}) is mapped to the catalog's {@link BookInfo} and its format
 * goes no further. Whatever goes wrong (the network, a timeout, an error status, a body that is not what was
 * expected) is logged without the body and answered as empty, never thrown (RNF19). What the source answered, found
 * or not, is kept for a short time; a failure is not kept, so the next ask tries again.
 */
class OpenLibraryBookLookup implements BookLookup {

    private static final Logger log = LoggerFactory.getLogger(OpenLibraryBookLookup.class);
    private static final Pattern YEAR = Pattern.compile("(?<!\\d)(\\d{4})(?!\\d)");
    private static final int MAX_BODY_CHARS = 1_000_000;

    private final HttpClient client;
    private final String baseUrl;
    private final IsbnLookupProperties properties;
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final TtlCache<String, Optional<BookInfo>> cache;

    OpenLibraryBookLookup(IsbnLookupProperties properties, Clock clock) {
        this.properties = properties;
        this.baseUrl = properties.baseUrl().replaceAll("/+$", "");
        this.client = HttpClient.newBuilder().connectTimeout(properties.connectionTimeout())
                .followRedirects(HttpClient.Redirect.NEVER).build();
        this.cache = new TtlCache<>(clock, properties.cacheTtl(), properties.cacheMaxSize());
    }

    @Override
    public Optional<BookInfo> findByIsbn(Isbn isbn) {
        var cached = cache.get(isbn.value());
        if (cached.isPresent()) {
            return cached.get();
        }
        try {
            var found = ask(isbn.value());
            cache.put(isbn.value(), found);
            return found;
        } catch (IOException | RuntimeException e) {
            log.warn("The lookup of an ISBN in Open Library failed ({}); the registration goes on by hand.",
                    e.getClass().getSimpleName());
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    private Optional<BookInfo> ask(String isbn) throws IOException, InterruptedException {
        var key = "ISBN:" + isbn;
        var uri = URI.create(baseUrl + "/api/books?bibkeys=" + key + "&format=json&jscmd=data");
        var request = HttpRequest.newBuilder(uri).timeout(properties.timeout())
                .header("Accept", "application/json").header("User-Agent", "BooksManager/1.0").GET().build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("unexpected status " + response.statusCode());
        }
        var body = response.body();
        if (body.length() > MAX_BODY_CHARS) {
            throw new IOException("the answer is too big");
        }
        var book = mapper.readTree(body).path(key);
        return book.isObject() ? Optional.ofNullable(toBookInfo(book)) : Optional.empty();
    }

    /** The mapped book, or null when the answer has no usable title (it is not worth suggesting). */
    private static BookInfo toBookInfo(JsonNode book) {
        var title = text(book.path("title"));
        if (title == null) {
            return null;
        }
        var subtitle = text(book.path("subtitle"));
        return new BookInfo(subtitle == null ? title : title + ": " + subtitle, names(book.path("authors")),
                first(names(book.path("publishers"))), year(text(book.path("publish_date"))));
    }

    private static List<String> names(JsonNode array) {
        var names = new ArrayList<String>();
        if (array.isArray()) {
            for (var item : array) {
                var name = text(item.path("name"));
                if (name != null) {
                    names.add(name);
                }
            }
        }
        return names;
    }

    private static String first(List<String> names) {
        return names.isEmpty() ? null : names.get(0);
    }

    private static String text(JsonNode node) {
        if (!node.isString()) {
            return null;
        }
        var value = node.asString().strip();
        return value.isEmpty() ? null : value;
    }

    private static Integer year(String publishDate) {
        if (publishDate == null) {
            return null;
        }
        var matcher = YEAR.matcher(publishDate);
        return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
    }
}
