package com.riverfount.booksmanager.user.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class GuardianTest {

    @Test
    void shouldCreateGuardianWithNameAndEmailAndOptionalPhone() {
        var guardian = new Guardian("Ana Souza", "ana@example.com", null);

        assertThat(guardian.name()).isEqualTo("Ana Souza");
        assertThat(guardian.email()).isEqualTo("ana@example.com");
        assertThat(guardian.phone()).isNull();
    }

    @Test
    void shouldAcceptGuardianPhoneWhenValid() {
        var guardian = new Guardian("Ana Souza", "ana@example.com", "+55 (11) 91234-5678");

        assertThat(guardian.phone()).isEqualTo("+55 (11) 91234-5678");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", " Ana", "Ana "})
    void shouldRejectGuardianWithoutNameOrWithSurroundingSpaces(String name) {
        assertThatThrownBy(() -> new Guardian(name, "ana@example.com", null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("responsável");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"ana", "ana@", "@example.com", "ana@example", "ana @example.com"})
    void shouldRejectGuardianWithoutEmailOrWithInvalidEmail(String email) {
        assertThatThrownBy(() -> new Guardian("Ana Souza", email, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("responsável");
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "123", "11 9999-99x9"})
    void shouldRejectGuardianWithInvalidPhone(String phone) {
        assertThatThrownBy(() -> new Guardian("Ana Souza", "ana@example.com", phone))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("responsável");
    }
}
