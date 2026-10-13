package com.riverfount.booksmanager.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PageResultTest {

    @Test
    void shouldComputeTotalPagesRoundingUp() {
        var result = new PageResult<>(List.of("a", "b"), 0, 2, 5);

        assertThat(result.totalPages()).isEqualTo(3);
    }

    @Test
    void shouldHaveZeroPagesWhenThereAreNoItems() {
        var result = new PageResult<String>(List.of(), 0, 20, 0);

        assertThat(result.totalPages()).isZero();
    }

    @Test
    void shouldMapItemsKeepingPaginationMetadata() {
        var result = new PageResult<>(List.of("a", "bb"), 1, 2, 4);

        var mapped = result.map(String::length);

        assertThat(mapped.items()).containsExactly(1, 2);
        assertThat(mapped.page()).isEqualTo(1);
        assertThat(mapped.size()).isEqualTo(2);
        assertThat(mapped.totalItems()).isEqualTo(4);
    }

    @Test
    void shouldNotExposeTheOriginalMutableList() {
        var items = new ArrayList<>(List.of("a"));
        var result = new PageResult<>(items, 0, 20, 1);

        items.add("b");

        assertThat(result.items()).containsExactly("a");
    }
}
