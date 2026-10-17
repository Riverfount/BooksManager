package com.riverfount.booksmanager.shared.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.info.BuildProperties;

/** The version the Swagger shows: the one of the project when the build information exists, "dev" when it does not. */
class OpenApiConfigVersionTest {

    private final OpenApiConfig config = new OpenApiConfig();

    @Test
    void shouldShowTheVersionOfTheProjectWithTheLetterV() {
        var factory = new DefaultListableBeanFactory();
        var properties = new Properties();
        properties.setProperty("version", "1.2.3");
        factory.registerSingleton("buildProperties", new BuildProperties(properties));

        var openApi = config.booksManagerOpenApi(factory.getBeanProvider(BuildProperties.class));

        assertThat(openApi.getInfo().getVersion()).isEqualTo("v1.2.3");
    }

    @Test
    void shouldShowDevWhenThereIsNoBuildInformation() {
        var factory = new DefaultListableBeanFactory();

        var openApi = config.booksManagerOpenApi(factory.getBeanProvider(BuildProperties.class));

        assertThat(openApi.getInfo().getVersion()).isEqualTo("dev");
    }

    @Test
    void theTitleAndTheDescriptionShouldNotDependOnTheVersion() {
        var openApi = config.booksManagerOpenApi(new DefaultListableBeanFactory().getBeanProvider(BuildProperties.class));

        assertThat(openApi.getInfo().getTitle()).isEqualTo("Books Manager API");
        assertThat(openApi.getInfo().getDescription()).isNotBlank();
    }
}
