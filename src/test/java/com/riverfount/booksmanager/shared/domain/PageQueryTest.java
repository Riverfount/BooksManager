package com.riverfount.booksmanager.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PageQueryTest {

    @Test
    void shouldParseSortFieldAndDirection() {
        var query = PageQuery.of(2, 10, "name,desc");

        assertThat(query.page()).isEqualTo(2);
        assertThat(query.size()).isEqualTo(10);
        assertThat(query.sortBy()).isEqualTo("name");
        assertThat(query.direction()).isEqualTo(SortDirection.DESC);
    }

    @Test
    void shouldDefaultToAscendingWhenDirectionIsOmitted() {
        var query = PageQuery.of(0, 20, "name");

        assertThat(query.direction()).isEqualTo(SortDirection.ASC);
    }

    @Test
    void shouldAcceptDirectionInAnyCase() {
        assertThat(PageQuery.of(0, 20, "name,DESC").direction()).isEqualTo(SortDirection.DESC);
    }

    @Test
    void shouldAcceptMaximumPageSize() {
        assertThat(PageQuery.of(0, PageQuery.MAX_SIZE, "name").size()).isEqualTo(100);
    }

    @Test
    void shouldRejectNegativePage() {
        assertThatThrownBy(() -> PageQuery.of(-1, 20, "name"))
                .isInstanceOf(InvalidQueryException.class)
                .hasMessageContaining("página");
    }

    @Test
    void shouldRejectPageSizeAboveMaximum() {
        // RNF14: page size is limited to 100 items
        assertThatThrownBy(() -> PageQuery.of(0, PageQuery.MAX_SIZE + 1, "name"))
                .isInstanceOf(InvalidQueryException.class)
                .hasMessageContaining("100");
    }

    @Test
    void shouldRejectPageSizeBelowOne() {
        assertThatThrownBy(() -> PageQuery.of(0, 0, "name"))
                .isInstanceOf(InvalidQueryException.class);
    }

    @Test
    void shouldRejectUnknownSortDirection() {
        assertThatThrownBy(() -> PageQuery.of(0, 20, "name,sideways"))
                .isInstanceOf(InvalidQueryException.class)
                .hasMessageContaining("sideways");
    }

    @Test
    void shouldRejectBlankSortField() {
        assertThatThrownBy(() -> PageQuery.of(0, 20, " ,asc"))
                .isInstanceOf(InvalidQueryException.class);
    }

    @Test
    void shouldRejectSortWithTooManyParts() {
        assertThatThrownBy(() -> PageQuery.of(0, 20, "name,asc,extra"))
                .isInstanceOf(InvalidQueryException.class);
    }
}
