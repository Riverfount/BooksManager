package com.riverfount.booksmanager.user.adapter.in.startup;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class AdminPropertiesTest {

    @Configuration
    @EnableConfigurationProperties(AdminProperties.class)
    static class Config {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Config.class);

    @Test
    void shouldBindTheLoginAndThePasswordFromTheProperties() {
        runner.withPropertyValues("app.admin.login=admin", "app.admin.password=S3cret-Pass!")
                .run(context -> {
                    var properties = context.getBean(AdminProperties.class);
                    assertThat(properties.login()).isEqualTo("admin");
                    assertThat(properties.password()).isEqualTo("S3cret-Pass!");
                });
    }

    @Test
    void shouldBeEmptyByDefault() {
        runner.run(context -> {
            var properties = context.getBean(AdminProperties.class);
            assertThat(properties.login()).isNullOrEmpty();
            assertThat(properties.password()).isNullOrEmpty();
        });
    }

    @Test
    void shouldNotShowThePasswordInItsStringForm() {
        var properties = new AdminProperties("admin", "S3cret-Pass!");

        assertThat(properties.toString()).doesNotContain("S3cret-Pass!").contains("admin");
    }
}
