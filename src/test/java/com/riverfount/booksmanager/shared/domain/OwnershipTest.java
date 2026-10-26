package com.riverfount.booksmanager.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** What a reader may reach is what is theirs (Phase 6): anything else is "not found", never "forbidden". */
class OwnershipTest {

    @Test
    void shouldAcceptWhatBelongsToTheCaller() {
        assertThatCode(() -> Ownership.requireOwner(7L, 7L, "empréstimo não encontrado: 3")).doesNotThrowAnyException();
        assertThat(Ownership.isOwner(7L, 7L)).isTrue();
    }

    @Test
    void shouldAnswerNotFoundForWhatBelongsToAnotherReader() {
        assertThatThrownBy(() -> Ownership.requireOwner(8L, 7L, "empréstimo não encontrado: 3"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("empréstimo não encontrado: 3");
        assertThat(Ownership.isOwner(8L, 7L)).isFalse();
    }

    @Test
    void shouldAnswerTheSameForWhatDoesNotExistSoNothingIsRevealed() {
        Throwable another = catchThrowable(() -> Ownership.requireOwner(8L, 7L, "reserva não encontrada: 3"));
        Throwable missing = catchThrowable(() -> Ownership.requireOwner(null, 7L, "reserva não encontrada: 3"));

        assertThat(another).isInstanceOf(ResourceNotFoundException.class);
        assertThat(missing).isInstanceOf(ResourceNotFoundException.class).hasMessage(another.getMessage());
    }

    @Test
    void shouldNeverAcceptWhenTheCallerIsUnknown() {
        assertThatThrownBy(() -> Ownership.requireOwner(7L, null, "empréstimo não encontrado: 3"))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(Ownership.isOwner(null, null)).isFalse();
    }

    private static Throwable catchThrowable(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            return e;
        }
        return null;
    }
}
