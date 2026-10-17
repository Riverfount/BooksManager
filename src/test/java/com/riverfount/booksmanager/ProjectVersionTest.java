package com.riverfount.booksmanager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The version of the project has one source, the {@code pom.xml}. The Swagger shows it, and the README
 * must say the same one. The version of the project is not the version of the API: the routes stay
 * under {@code /api/v1}, whatever the version of the project is.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class ProjectVersionTest {

    private static final Pattern README_VERSION = Pattern.compile("\\*\\*Versão atual\\*\\*\\s*\\|\\s*(\\S+)\\s*\\|");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BuildProperties buildProperties;

    private JsonNode contract() throws Exception {
        var body = mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString();
        return new ObjectMapper().readTree(body);
    }

    @Test
    void theSwaggerShouldShowTheVersionOfTheProject() throws Exception {
        assertThat(contract().path("info").path("version").asText())
                .isEqualTo("v" + buildProperties.getVersion());
    }

    @Test
    void theVersionOfTheProjectShouldNotChangeThePrefixOfTheRoutes() throws Exception {
        var paths = new ArrayList<String>();
        contract().path("paths").fieldNames().forEachRemaining(paths::add);

        assertThat(paths).isNotEmpty().allMatch(path -> path.startsWith("/api/v1/"));
    }

    @Test
    void theReadmeShouldShowTheSameVersionAsThePom() throws Exception {
        var readme = Files.readString(Path.of("README.md"));
        var matcher = README_VERSION.matcher(readme);

        assertThat(matcher.find()).as("the README has a 'Versão atual' row").isTrue();
        assertThat(matcher.group(1)).isEqualTo(buildProperties.getVersion());
    }
}
