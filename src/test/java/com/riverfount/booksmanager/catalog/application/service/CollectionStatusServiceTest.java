package com.riverfount.booksmanager.catalog.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.catalog.application.port.in.CollectionStatus;
import com.riverfount.booksmanager.catalog.domain.Copy;
import com.riverfount.booksmanager.catalog.domain.CopyStatus;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The status of the collection (RF25, RN14): the active copies in all and by status, every status present
 * (zero when empty), and the inactive ones apart.
 */
class CollectionStatusServiceTest {

    private InMemoryCopyRepository copyRepository;
    private CollectionStatusService service;
    private int sequence;

    @BeforeEach
    void setUp() {
        copyRepository = new InMemoryCopyRepository();
        service = new CollectionStatusService(copyRepository);
    }

    private void copy(CopyStatus status, boolean active) {
        copyRepository.save(Copy.reconstitute(null, 1L, "PAT-" + ++sequence, status, active, 0L));
    }

    @Test
    void shouldCountTheActiveCopiesByStatusAndInTotal() {
        copy(CopyStatus.AVAILABLE, true);
        copy(CopyStatus.AVAILABLE, true);
        copy(CopyStatus.AVAILABLE, true);
        copy(CopyStatus.BORROWED, true);
        copy(CopyStatus.BORROWED, true);
        copy(CopyStatus.RESERVED, true);
        copy(CopyStatus.DAMAGED, true);
        copy(CopyStatus.LOST, true);

        var status = service.get();

        assertThat(status.totalActive()).isEqualTo(8);
        assertThat(status.activeByStatus()).containsExactly(
                Map.entry(CopyStatus.AVAILABLE, 3L), Map.entry(CopyStatus.BORROWED, 2L),
                Map.entry(CopyStatus.RESERVED, 1L), Map.entry(CopyStatus.DAMAGED, 1L), Map.entry(CopyStatus.LOST, 1L));
        assertThat(status.inactive()).isZero();
    }

    @Test
    void aStatusWithNoCopiesShouldBePresentWithZero() {
        copy(CopyStatus.AVAILABLE, true);

        var status = service.get();

        assertThat(status.activeByStatus()).containsOnlyKeys(CopyStatus.values());
        assertThat(status.activeByStatus().get(CopyStatus.BORROWED)).isZero();
        assertThat(status.activeByStatus().get(CopyStatus.LOST)).isZero();
    }

    @Test
    void anInactiveCopyShouldCountApartAndNeverAsActive() {
        copy(CopyStatus.AVAILABLE, true);
        copy(CopyStatus.AVAILABLE, false);
        copy(CopyStatus.LOST, false);
        copy(CopyStatus.BORROWED, false);

        var status = service.get();

        assertThat(status.totalActive()).isEqualTo(1);
        assertThat(status.activeByStatus().get(CopyStatus.AVAILABLE)).isEqualTo(1);
        assertThat(status.activeByStatus().get(CopyStatus.LOST)).isZero();
        assertThat(status.inactive()).isEqualTo(3);
    }

    @Test
    void theCountsByStatusShouldAlwaysAddUpToTheTotal() {
        for (var status : CopyStatus.values()) {
            copy(status, true);
            copy(status, true);
            copy(status, false);
        }

        var result = service.get();

        assertThat(result.activeByStatus().values().stream().mapToLong(Long::longValue).sum())
                .isEqualTo(result.totalActive()).isEqualTo(10);
        assertThat(result.inactive()).isEqualTo(5);
    }

    @Test
    void anEmptyCollectionShouldAnswerAllZeros() {
        var status = service.get();

        assertThat(status.totalActive()).isZero();
        assertThat(status.inactive()).isZero();
        assertThat(status.activeByStatus().values()).containsOnly(0L);
        assertThat(status.activeByStatus()).hasSize(CopyStatus.values().length);
    }

    @Test
    void theStatusesShouldComeInTheOrderOfTheEnum() {
        copy(CopyStatus.LOST, true);

        assertThat(List.copyOf(service.get().activeByStatus().keySet()))
                .containsExactly(CopyStatus.values());
    }

    @Test
    void theAnswerShouldNotBeChangeable() {
        var status = service.get();

        assertThatThrownBy(() -> status.activeByStatus().put(CopyStatus.LOST, 9L))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(new CollectionStatus(0, Map.of(), 0).activeByStatus()).isEmpty();
    }
}
