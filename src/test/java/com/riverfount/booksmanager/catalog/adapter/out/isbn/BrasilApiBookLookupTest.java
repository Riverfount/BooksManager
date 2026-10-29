package com.riverfount.booksmanager.catalog.adapter.out.isbn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.riverfount.booksmanager.catalog.domain.Isbn;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * RF06, RNF19: the BrasilAPI adapter against a simulated HTTP server (the JDK's own), never the real network. A good
 * answer is mapped to the catalog's language; an empty, slow, failing or malformed one comes back as empty, without
 * an exception; and an answer is kept for a short time, but a failure is not.
 */
class BrasilApiBookLookupTest {

    private static final Isbn ISBN = new Isbn("9780000000309");

    private HttpServer server;
    private final AtomicInteger requests = new AtomicInteger();
    private final AtomicReference<String> lastPath = new AtomicReference<>();
    private final AtomicReference<String> lastUserAgent = new AtomicReference<>();
    private volatile int status = 200;
    private volatile String body = "{}";
    private volatile long delayMillis;

    private Instant now = Instant.parse("2026-11-10T12:00:00Z");
    private final Clock clock = new Clock() {
        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    };

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/isbn/v1", exchange -> {
            requests.incrementAndGet();
            lastPath.set(exchange.getRequestURI().getRawPath());
            lastUserAgent.set(exchange.getRequestHeaders().getFirst("User-Agent"));
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            var bytes = body.getBytes(StandardCharsets.UTF_8);
            try {
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, bytes.length);
                exchange.getResponseBody().write(bytes);
            } catch (IOException ignored) {
                // the client gave up (a timeout), which is the point of some tests
            } finally {
                exchange.close();
            }
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private BrasilApiBookLookup lookup(Duration timeout) {
        var properties = new IsbnLookupProperties(true, "http://127.0.0.1:" + server.getAddress().getPort() + "/",
                Duration.ofSeconds(1), timeout, Duration.ofMinutes(10), 100);
        return new BrasilApiBookLookup(properties, clock);
    }

    private BrasilApiBookLookup lookup() {
        return lookup(Duration.ofSeconds(2));
    }

    private static String book(String fields) {
        return "{" + fields + "}";
    }

    // the good answer

    @Test
    void shouldMapAKnownIsbnToTheCatalogsLanguage() {
        body = book("""
                "isbn": "9780000000309", "title": "Dom Casmurro", "subtitle": "romance",
                "authors": ["Machado de Assis", "Outro Autor"], "publisher": "Editora Garnier", "year": 1899,
                "provider": "cbl"
                """);

        var info = lookup().findByIsbn(ISBN).orElseThrow();

        assertThat(info.title()).isEqualTo("Dom Casmurro: romance");
        assertThat(info.authors()).containsExactly("Machado de Assis", "Outro Autor");
        assertThat(info.publisher()).isEqualTo("Editora Garnier");
        assertThat(info.publicationYear()).isEqualTo(1899);
    }

    @Test
    void shouldAcceptABookWithOnlyATitle() {
        body = book("\"title\": \"Só o título\", \"subtitle\": null, \"authors\": [], \"publisher\": null, \"year\": null");

        var info = lookup().findByIsbn(ISBN).orElseThrow();

        assertThat(info.title()).isEqualTo("Só o título");
        assertThat(info.authors()).isEmpty();
        assertThat(info.publisher()).isNull();
        assertThat(info.publicationYear()).isNull();
    }

    @Test
    void shouldIgnoreFieldsOfTheWrongTypeAndBlankNames() {
        body = book("""
                "title": "T", "authors": [5, {"x": "y"}, "  "], "publisher": 7, "year": "unknown"
                """);

        var info = lookup().findByIsbn(ISBN).orElseThrow();

        assertThat(info.authors()).isEmpty();
        assertThat(info.publisher()).isNull();
        assertThat(info.publicationYear()).isNull();
    }

    @Test
    void shouldAcceptAYearSentAsText() {
        body = book("\"title\": \"T\", \"year\": \"2009\"");

        assertThat(lookup().findByIsbn(ISBN).orElseThrow().publicationYear()).isEqualTo(2009);
    }

    @Test
    void shouldAskForTheNormalizedIsbnAndIdentifyItself() {
        body = book("\"title\": \"T\"");

        lookup().findByIsbn(new Isbn("978-0-00-000030-9"));

        assertThat(lastPath.get()).isEqualTo("/api/isbn/v1/9780000000309");
        assertThat(lastUserAgent.get()).startsWith("BooksManager/");
    }

    // what is not a book

    @Test
    void aNotFoundStatusMeansTheSourceDoesNotKnowTheIsbn() {
        status = 404;
        body = "{\"message\": \"ISBN não encontrado\", \"type\": \"not_found\"}";

        assertThat(lookup().findByIsbn(ISBN)).isEmpty();
    }

    @Test
    void shouldNotSuggestABookWithoutATitle() {
        body = book("\"authors\": [\"A\"]");

        assertThat(lookup().findByIsbn(ISBN)).isEmpty();
    }

    // the failures: never an exception

    @Test
    void aSlowSourceShouldBeGivenUpOnWithinTheTimeout() {
        body = book("\"title\": \"T\"");
        delayMillis = 3000;
        var started = System.nanoTime();

        var found = lookup(Duration.ofMillis(300)).findByIsbn(ISBN);

        assertThat(found).isEmpty();
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(2));
    }

    @Test
    void anInvalidIsbnStatusShouldCountAsUnavailableAndNotBeKept() {
        status = 400;
        var lookup = lookup();

        lookup.findByIsbn(ISBN);
        lookup.findByIsbn(ISBN);

        assertThat(requests.get()).isEqualTo(2);
    }

    @Test
    void anErrorStatusShouldCountAsUnavailable() {
        status = 500;
        body = book("\"title\": \"T\"");

        assertThat(lookup().findByIsbn(ISBN)).isEmpty();
    }

    @Test
    void aRedirectShouldNotBeFollowed() {
        status = 302;

        assertThat(lookup().findByIsbn(ISBN)).isEmpty();
    }

    @Test
    void aMalformedBodyShouldNotRaiseAnError() {
        body = "<html>not json";

        assertThatCode(() -> lookup().findByIsbn(ISBN)).doesNotThrowAnyException();
        assertThat(lookup().findByIsbn(ISBN)).isEmpty();
    }

    @Test
    void anUnexpectedShapeShouldBeTreatedAsNoBook() {
        for (var strange : new String[] {"[]", "null", "\"text\"", "5", "{}", "{\"title\": 7}"}) {
            body = strange;
            assertThat(lookup().findByIsbn(ISBN)).as(strange).isEmpty();
        }
    }

    @Test
    void anAnswerBiggerThanAnyBookShouldBeRefused() {
        body = book("\"title\": \"" + "x".repeat(1_100_000) + "\"");

        assertThat(lookup().findByIsbn(ISBN)).isEmpty();
    }

    @Test
    void aSourceThatIsDownShouldBeAnsweredAsEmpty() {
        var lookup = lookup();
        server.stop(0);

        assertThat(lookup.findByIsbn(ISBN)).isEmpty();
    }

    // the cache

    @Test
    void shouldNotAskAgainForTheSameIsbnWhileTheAnswerIsFresh() {
        body = book("\"title\": \"T\"");
        var lookup = lookup();

        lookup.findByIsbn(ISBN);
        var again = lookup.findByIsbn(ISBN);

        assertThat(again).isPresent();
        assertThat(requests.get()).isEqualTo(1);
    }

    @Test
    void shouldAlsoKeepThatTheSourceDoesNotKnowTheIsbn() {
        status = 404;
        var lookup = lookup();

        lookup.findByIsbn(ISBN);
        lookup.findByIsbn(ISBN);

        assertThat(requests.get()).isEqualTo(1);
    }

    @Test
    void shouldAskAgainWhenTheAnswerExpired() {
        body = book("\"title\": \"Old\"");
        var lookup = lookup();
        lookup.findByIsbn(ISBN);

        now = now.plus(Duration.ofMinutes(11));
        body = book("\"title\": \"New\"");

        assertThat(lookup.findByIsbn(ISBN).orElseThrow().title()).isEqualTo("New");
        assertThat(requests.get()).isEqualTo(2);
    }

    @Test
    void aFailureShouldNotBeKeptSoTheNextAskTriesAgain() {
        var lookup = lookup();
        status = 503;
        assertThat(lookup.findByIsbn(ISBN)).isEmpty();

        status = 200;
        body = book("\"title\": \"Back\"");

        assertThat(lookup.findByIsbn(ISBN).orElseThrow().title()).isEqualTo("Back");
        assertThat(requests.get()).isEqualTo(2);
    }
}
