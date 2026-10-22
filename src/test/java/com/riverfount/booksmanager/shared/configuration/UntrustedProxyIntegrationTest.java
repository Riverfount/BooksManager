package com.riverfount.booksmanager.shared.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import com.riverfount.booksmanager.TestTokens;
import com.riverfount.booksmanager.TestcontainersConfiguration;
import com.riverfount.booksmanager.security.configuration.JwtProperties;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtEncoder;

/**
 * A client that is not a trusted proxy cannot forge the {@code X-Forwarded-*} headers (RNF12): with the production
 * profile, the headers are honored only when the request comes from an internal proxy address. Here the loopback
 * address of the test client is taken out of the trusted addresses, which makes it look like a stranger that reaches
 * the application directly, and the forged headers must be ignored.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.profiles.active=prod", "app.reservations.expiration.enabled=false",
                "server.tomcat.remoteip.internal-proxies=10\\.99\\.99\\.99"})
class UntrustedProxyIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtEncoder encoder;

    @Autowired
    private JwtProperties properties;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from category where name = 'Untrusted proxy test'");
    }

    @Test
    void forgedProxyHeadersFromAStrangerShouldBeIgnored() throws Exception {
        var token = TestTokens.signed(encoder, properties.issuer(), "1", "ADMIN", Instant.now(),
                Instant.now().plusSeconds(900));
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/categories"))
                .header("Authorization", "Bearer " + token).header("Content-Type", "application/json")
                .header("X-Forwarded-Proto", "https").header("X-Forwarded-Host", "evil.example.com")
                .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"Untrusted proxy test\"}")).build();

        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.headers().firstValue("Location").orElseThrow())
                .startsWith("http://localhost:" + port + "/api/v1/categories/").doesNotContain("evil.example.com");
    }
}
