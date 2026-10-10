package com.riverfount.booksmanager.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BusinessRuleExceptionTest {

    @Test
    void shouldKeepTheReceivedMessage() {
        var exception = new BusinessRuleException("o nome é obrigatório");

        assertThat(exception.getMessage()).isEqualTo("o nome é obrigatório");
    }

    @Test
    void shouldBeAnUncheckedRuntimeException() {
        var exception = new BusinessRuleException("qualquer mensagem");

        assertThat(exception).isInstanceOf(RuntimeException.class);
    }
}
