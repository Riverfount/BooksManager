package com.riverfount.booksmanager.user.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class PasswordPolicyTest {

    @ParameterizedTest
    @ValueSource(strings = {"Abcdef1!", "S3cret-Pass", "Ação123!", "Abc def1!", "Zz9#" + "x1234", "aA1_bcde"})
    void shouldAcceptAPasswordThatMeetsEveryRule(String password) {
        assertThatCode(() -> PasswordPolicy.validate(password)).doesNotThrowAnyException();
    }

    @Test
    void shouldAcceptTheMinimumAndTheMaximumLength() {
        assertThatCode(() -> PasswordPolicy.validate("Abcde1!x")).doesNotThrowAnyException();
        assertThatCode(() -> PasswordPolicy.validate("Aa1!" + "x".repeat(PasswordPolicy.MAX_LENGTH - 4)))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectAPasswordShorterThanTheMinimum() {
        assertThatThrownBy(() -> PasswordPolicy.validate("Abcde1!"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("8")
                .hasMessageContaining("25");
    }

    @Test
    void shouldRejectAPasswordLongerThanTheMaximum() {
        var password = "Aa1!" + "x".repeat(PasswordPolicy.MAX_LENGTH - 3);

        assertThat(password).hasSize(PasswordPolicy.MAX_LENGTH + 1);
        assertThatThrownBy(() -> PasswordPolicy.validate(password))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("25");
    }

    @Test
    void shouldRejectAPasswordWithoutAnUppercaseLetter() {
        assertThatThrownBy(() -> PasswordPolicy.validate("abcdef1!"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("maiúscula")
                .hasMessageNotContaining("minúscula")
                .hasMessageNotContaining("número")
                .hasMessageNotContaining("especial");
    }

    @Test
    void shouldRejectAPasswordWithoutALowercaseLetter() {
        assertThatThrownBy(() -> PasswordPolicy.validate("ABCDEF1!"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("minúscula");
    }

    @Test
    void shouldRejectAPasswordWithoutADigit() {
        assertThatThrownBy(() -> PasswordPolicy.validate("Abcdefg!"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("número");
    }

    @Test
    void shouldRejectAPasswordWithoutASpecialCharacter() {
        assertThatThrownBy(() -> PasswordPolicy.validate("Abcdefg1"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("especial");
    }

    @Test
    void aSpaceDoesNotCountAsASpecialCharacter() {
        assertThatThrownBy(() -> PasswordPolicy.validate("Abcdef 1"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("especial");
    }

    @Test
    void shouldListEveryUnmetRuleInASingleMessage() {
        assertThatThrownBy(() -> PasswordPolicy.validate("abc"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("8")
                .hasMessageContaining("maiúscula")
                .hasMessageContaining("número")
                .hasMessageContaining("especial");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void shouldRejectAMissingPassword(String password) {
        assertThatThrownBy(() -> PasswordPolicy.validate(password))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("obrigatória");
    }

    @Test
    void theMessageShouldNeverRepeatThePassword() {
        assertThatThrownBy(() -> PasswordPolicy.validate("weak-secret"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageNotContaining("weak-secret");
    }

    @Test
    void shouldRejectAPasswordTooBigForBcryptEvenWithinTheCharacterLimit() {
        // 25 characters, but each emoji takes 4 bytes: 88 bytes, above the 72 BCrypt can use
        var password = "Aa1!" + "😀".repeat(21);

        assertThat(password.codePointCount(0, password.length())).isEqualTo(25);
        assertThatThrownBy(() -> PasswordPolicy.validate(password))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("72");
    }
}
