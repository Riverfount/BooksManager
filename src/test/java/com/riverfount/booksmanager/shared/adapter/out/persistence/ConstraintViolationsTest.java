package com.riverfount.booksmanager.shared.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class ConstraintViolationsTest {

    @Test
    void shouldRecognizeWhenTheMostSpecificCauseMentionsTheConstraintName() {
        var cause = new RuntimeException("duplicate key value violates unique constraint \"book_isbn_key\"");
        var exception = new DataIntegrityViolationException("erro ao salvar", cause);

        assertThat(ConstraintViolations.violates(exception, "book_isbn_key")).isTrue();
    }

    @Test
    void shouldNotRecognizeWhenTheCauseMentionsAnotherConstraint() {
        var cause = new RuntimeException(
                "insert or update on table \"book\" violates foreign key constraint \"book_category_id_fkey\"");
        var exception = new DataIntegrityViolationException("erro ao salvar", cause);

        assertThat(ConstraintViolations.violates(exception, "book_isbn_key")).isFalse();
    }

    @Test
    void shouldNotRecognizeWhenThereIsNoCauseWithACompatibleMessage() {
        var exception = new DataIntegrityViolationException("erro genérico, sem a constraint na mensagem");

        assertThat(ConstraintViolations.violates(exception, "book_isbn_key")).isFalse();
    }
}
