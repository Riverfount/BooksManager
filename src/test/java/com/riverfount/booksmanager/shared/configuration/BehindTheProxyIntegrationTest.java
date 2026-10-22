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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;

/**
 * The application behind a reverse proxy that ends the TLS, with the production profile (RNF09, RNF10, RNF12,
 * RNF23), over a real HTTP server: a request that comes with the {@code X-Forwarded-*} headers of the proxy gets an
 * {@code https} Location, the refresh cookie stays Secure, and only the health endpoint is exposed, with no detail.
 * The requests come from the loopback address, which is an internal address, so the proxy is trusted as it is in
 * a Docker network. The reservations routine is turned off here, as in every test. The data is committed, so it is
 * removed after each test.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.profiles.active=prod", "app.reservations.expiration.enabled=false"})
class BehindTheProxyIntegrationTest {

    private static final String PASSWORD = "S3cret-Pass!";

    private final HttpClient client = HttpClient.newHttpClient();

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
        jdbcTemplate.update("delete from category where name like 'Proxy test%'");
        jdbcTemplate.update("delete from refresh_token where user_id in (select id from app_user where login like 'proxy-test-%')");
        jdbcTemplate.update("delete from app_user where login like 'proxy-test-%'");
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private HttpResponse<String> createCategory(String name, boolean throughTheProxy) throws Exception {
        var token = TestTokens.signed(encoder, properties.issuer(), "1", "ADMIN", Instant.now(),
                Instant.now().plusSeconds(900));
        var request = HttpRequest.newBuilder(uri("/api/v1/categories"))
                .header("Authorization", "Bearer " + token).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"" + name + "\"}"));
        if (throughTheProxy) {
            request.header("X-Forwarded-Proto", "https").header("X-Forwarded-Host", "biblioteca.example.com")
                    .header("X-Forwarded-Port", "443");
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void theLocationOfA201ShouldBeHttpsWithThePublicHostWhenTheProxyAsksForIt() throws Exception {
        var response = createCategory("Proxy test A", true);

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.headers().firstValue("Location").orElseThrow())
                .startsWith("https://biblioteca.example.com/api/v1/categories/");
    }

    @Test
    void withoutTheProxyHeadersTheLocationShouldBeTheAddressOfTheRequest() throws Exception {
        var response = createCategory("Proxy test B", false);

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.headers().firstValue("Location").orElseThrow())
                .startsWith("http://localhost:" + port + "/api/v1/categories/");
    }

    @Test
    void theRefreshCookieShouldStayHttpOnlyAndSecureBehindTheProxy() throws Exception {
        jdbcTemplate.update("insert into app_user (login, password_hash, role, active) values (?, ?, 'OPERATOR', true)",
                "proxy-test-maria", new BCryptPasswordEncoder().encode(PASSWORD));
        var request = HttpRequest.newBuilder(uri("/api/v1/auth/login")).header("Content-Type", "application/json")
                .header("X-Forwarded-Proto", "https").header("X-Forwarded-Host", "biblioteca.example.com")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"login\":\"proxy-test-maria\",\"password\":\"" + PASSWORD + "\"}"))
                .build();

        var response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        var cookie = response.headers().allValues("Set-Cookie").stream()
                .filter(value -> value.startsWith("refresh_token=")).findFirst().orElseThrow();
        assertThat(cookie).contains("HttpOnly").contains("Secure").contains("SameSite=Strict");
    }

    @Test
    void theHealthEndpointShouldAnswerOnlyItsStatus() throws Exception {
        var response = client.send(HttpRequest.newBuilder(uri("/actuator/health")).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"UP\"").doesNotContain("components").doesNotContain("db");
    }

    @Test
    void noOtherActuatorEndpointShouldBeExposed() throws Exception {
        for (var path : new String[] {"/actuator", "/actuator/env", "/actuator/info", "/actuator/beans",
                "/actuator/metrics"}) {
            var response = client.send(HttpRequest.newBuilder(uri(path)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());

            assertThat(response.statusCode()).as(path).isNotEqualTo(200);
        }
    }

    @Test
    void theSwaggerAndTheOpenApiShouldStayPublic() throws Exception {
        var response = client.send(HttpRequest.newBuilder(uri("/v3/api-docs")).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"openapi\"");
    }

    @Test
    void anErrorShouldNotCarryAStackTraceToTheClient() throws Exception {
        var response = client.send(HttpRequest.newBuilder(uri("/api/v1/categories/not-a-number")).GET()
                .header("Authorization", "Bearer " + TestTokens.signed(encoder, properties.issuer(), "1", "ADMIN",
                        Instant.now(), Instant.now().plusSeconds(900))).build(), HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).doesNotContain("at com.riverfount").doesNotContain("trace");
    }
}
