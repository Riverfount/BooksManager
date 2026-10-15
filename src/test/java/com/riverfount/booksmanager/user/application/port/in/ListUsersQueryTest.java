package com.riverfount.booksmanager.user.application.port.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.shared.domain.InvalidQueryException;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ListUsersQueryTest {

    @ParameterizedTest
    @ValueSource(strings = {"id", "login"})
    void shouldAcceptTheSortableFields(String field) {
        var query = new ListUsersQuery(null, true, PageQuery.of(0, 20, field));

        assertThat(query.page().sortBy()).isEqualTo(field);
    }

    @ParameterizedTest
    @ValueSource(strings = {"passwordHash", "password_hash", "password", "role", "active", "blockedUntil"})
    void shouldRejectAnyOtherSortFieldInParticularThoseThatWouldLeakOrOrderBySecrets(String field) {
        var page = PageQuery.of(0, 20, field);

        assertThatThrownBy(() -> new ListUsersQuery(null, true, page))
                .isInstanceOf(InvalidQueryException.class)
                .hasMessageContaining(field);
    }

    @Test
    void shouldTreatABlankLoginAsAbsentAndStripTheOtherOne() {
        assertThat(new ListUsersQuery("   ", true, PageQuery.of(0, 20, "login")).login()).isNull();
        assertThat(new ListUsersQuery(null, true, PageQuery.of(0, 20, "login")).login()).isNull();
        assertThat(new ListUsersQuery("  Mar ", true, PageQuery.of(0, 20, "login")).login()).isEqualTo("Mar");
    }

    @Test
    void shouldRequirePageQuery() {
        assertThatThrownBy(() -> new ListUsersQuery(null, true, null)).isInstanceOf(NullPointerException.class);
    }
}
