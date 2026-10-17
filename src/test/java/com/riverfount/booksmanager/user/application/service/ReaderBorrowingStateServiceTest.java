package com.riverfount.booksmanager.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.shared.domain.ResourceNotFoundException;
import com.riverfount.booksmanager.user.domain.Reader;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** What the user module tells the loan module about a reader (RN11, RF10). */
class ReaderBorrowingStateServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-10-15T12:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 15);

    private InMemoryReaderRepository repository;
    private ReaderBorrowingStateService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryReaderRepository();
        service = new ReaderBorrowingStateService(repository, FIXED_CLOCK);
    }

    private Reader saveReader() {
        return repository.save(Reader.create("Maria Silva", "12345678900", "maria@example.com", null,
                LocalDate.of(1990, 5, 10), null, TODAY));
    }

    @Test
    void anActiveReaderThatIsNotBlockedShouldBeAbleToBorrow() {
        var reader = saveReader();

        var state = service.getBorrowingState(reader.getId());

        assertThat(state.readerId()).isEqualTo(reader.getId());
        assertThat(state.active()).isTrue();
        assertThat(state.blocked()).isFalse();
        assertThat(state.canBorrow()).isTrue();
    }

    @Test
    void aBlockedReaderShouldNotBeAbleToBorrow() {
        var reader = saveReader();
        reader.block();
        repository.save(reader);

        var state = service.getBorrowingState(reader.getId());

        assertThat(state.blocked()).isTrue();
        assertThat(state.active()).isTrue();
        assertThat(state.canBorrow()).isFalse();
    }

    @Test
    void anInactiveReaderShouldNotBeAbleToBorrow() {
        var reader = saveReader();
        reader.deactivate();
        repository.save(reader);

        var state = service.getBorrowingState(reader.getId());

        assertThat(state.active()).isFalse();
        assertThat(state.canBorrow()).isFalse();
    }

    @Test
    void aBlockedAndInactiveReaderShouldReportBothReasons() {
        var reader = saveReader();
        reader.block();
        reader.deactivate();
        repository.save(reader);

        var state = service.getBorrowingState(reader.getId());

        assertThat(state.active()).isFalse();
        assertThat(state.blocked()).isTrue();
        assertThat(state.canBorrow()).isFalse();
    }

    @Test
    void shouldFailWithNotFoundForAnUnknownReader() {
        assertThatThrownBy(() -> service.getBorrowingState(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999");
    }
}
