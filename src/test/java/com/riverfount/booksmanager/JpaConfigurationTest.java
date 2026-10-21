package com.riverfount.booksmanager;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;

/**
 * Verifies the JPA configuration required by RNF03: the schema is
 * created and altered only by Flyway, never by Hibernate.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class JpaConfigurationTest {

    @Autowired
    private Environment environment;

    @Test
    void shouldNotCreateOrAlterTheSchemaOnlyValidate() {
        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
    }

    @Test
    void shouldNotKeepTheConnectionOpenDuringViewRendering() {
        assertThat(environment.getProperty("spring.jpa.open-in-view")).isEqualTo("false");
    }
}
