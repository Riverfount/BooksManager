package com.riverfount.booksmanager;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;

/**
 * Verifica a configuração do JPA exigida pela RNF03: o esquema é criado e
 * alterado apenas pelo Flyway, nunca pelo Hibernate.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ConfiguracaoJpaTest {

    @Autowired
    private Environment environment;

    @Test
    void naoDeveGerarOuAlterarOEsquemaApenasValidar() {
        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
    }

    @Test
    void naoDeveManterAConexaoAbertaDuranteARenderizacaoDaView() {
        assertThat(environment.getProperty("spring.jpa.open-in-view")).isEqualTo("false");
    }
}
