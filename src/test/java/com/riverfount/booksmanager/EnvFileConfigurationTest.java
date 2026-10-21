package com.riverfount.booksmanager;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;

/**
 * The local {@code .env} file is read by the application (so the variables of
 * the README need not be exported by hand), but only in the "local" profile,
 * which the tests turn off: a developer's own .env must not leak into them.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class EnvFileConfigurationTest {

    @Autowired
    private Environment environment;

    @Test
    void theLocalProfileShouldImportTheEnvFileAndBeTheDefault() throws Exception {
        var local = new ClassPathResource("application-local.properties").getContentAsString(
                java.nio.charset.StandardCharsets.UTF_8);
        var main = new ClassPathResource("application.properties").getContentAsString(
                java.nio.charset.StandardCharsets.UTF_8);

        assertThat(local).contains("spring.config.import=optional:file:.env[.properties]");
        assertThat(main).contains("spring.profiles.default=local");
    }

    @Test
    void theTestsShouldRunWithoutTheLocalProfileSoTheDevelopersEnvFileIsNotRead() {
        assertThat(environment.getActiveProfiles()).containsExactly("test");
    }

    @Test
    void thePlaceholdersOfTheApplicationShouldResolveFromTheEnvFileEntries(@TempDir Path directory) throws Exception {
        var envFile = directory.resolve(".env");
        Files.writeString(envFile, """
                # comment line
                POSTGRES_DB=ignored
                APP_JWT_SECRET=secret-from-the-env-file-with-over-32-bytes-in-it==
                APP_ADMIN_LOGIN=
                """);

        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(Empty.class)
                .web(WebApplicationType.NONE)
                .properties("spring.config.import=optional:file:" + envFile + "[.properties]",
                        "spring.config.name=env-file-config-test",
                        "demo.secret=${APP_JWT_SECRET:}", "demo.login=${APP_ADMIN_LOGIN:}",
                        "demo.missing=${APP_SOMETHING_ELSE:}")
                .run()) {
            var env = context.getEnvironment();
            assertThat(env.getProperty("demo.secret")).isEqualTo("secret-from-the-env-file-with-over-32-bytes-in-it==");
            assertThat(env.getProperty("demo.login")).isEmpty();
            assertThat(env.getProperty("demo.missing")).isEmpty();
        }
    }

    @org.springframework.context.annotation.Configuration
    static class Empty {
    }
}
