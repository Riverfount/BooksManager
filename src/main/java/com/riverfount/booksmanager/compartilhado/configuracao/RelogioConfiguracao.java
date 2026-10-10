package com.riverfount.booksmanager.compartilhado.configuracao;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Expõe o {@code Clock} usado pelos serviços de aplicação, para que o
 * domínio nunca precise chamar {@code LocalDate.now()} diretamente. Em
 * produção é o relógio real do sistema; os testes usam
 * {@code Clock.fixed(...)} e não dependem deste bean.
 */
@Configuration
public class RelogioConfiguracao {

    @Bean
    Clock relogio() {
        return Clock.systemDefaultZone();
    }
}
