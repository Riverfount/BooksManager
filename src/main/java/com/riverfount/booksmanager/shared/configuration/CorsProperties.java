package com.riverfount.booksmanager.shared.configuration;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Origins allowed to call the API from a browser (RNF09), read from the
 * {@code app.cors.allowed-origins} property (environment variable
 * {@code APP_CORS_ALLOWED_ORIGINS}, comma separated). It is empty by
 * default: no origin is hardcoded (RNF10), so no cross-origin request is
 * allowed until the front-end origin is configured.
 */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    }
}
