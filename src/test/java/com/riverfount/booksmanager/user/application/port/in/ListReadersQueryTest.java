package com.riverfount.booksmanager.user.application.port.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.shared.domain.InvalidQueryException;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ListReadersQueryTest {

    @ParameterizedTest
    @ValueSource(strings = {"id", "name"})
    void shouldAcceptTheSortableFields(String field) {
        var query = new ListReadersQuery(null, null, true, null, PageQuery.of(0, 20, field));

        assertThat(query.page().sortBy()).isEqualTo(field);
    }

    @ParameterizedTest
    @ValueSource(strings = {"birthDate", "document", "email", "guardian"})
    void shouldRejectOtherSortFields(String field) {
        var page = PageQuery.of(0, 20, field);

        assertThatThrownBy(() -> new ListReadersQuery(null, null, true, null, page))
                .isInstanceOf(InvalidQueryException.class)
                .hasMessageContaining(field);
    }

    @Test
    void shouldTreatBlankNameAndDocumentAsAbsent() {
        var query = new ListReadersQuery("   ", "  ", true, null, PageQuery.of(0, 20, "name"));

        assertThat(query.name()).isNull();
        assertThat(query.document()).isNull();
    }

    @Test
    void shouldStripTheName() {
        var query = new ListReadersQuery("  Maria ", null, true, null, PageQuery.of(0, 20, "name"));

        assertThat(query.name()).isEqualTo("Maria");
    }

    @Test
    void shouldRequirePageQuery() {
        assertThatThrownBy(() -> new ListReadersQuery(null, null, true, null, null))
                .isInstanceOf(NullPointerException.class);
    }
}
