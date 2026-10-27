package com.riverfount.booksmanager.user.application.port.out;

import com.riverfount.booksmanager.user.domain.Role;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UserFilterTest {

    @Test
    void noneShouldHaveNoFilters() {
        var filter = UserFilter.none();

        assertThat(filter.login()).isNull();
        assertThat(filter.active()).isNull();
    }

    @Test
    void shouldNormalizeTheLoginLikeTheRegistration() {
        assertThat(new UserFilter("  Maria.SILVA ", null).login()).isEqualTo("maria.silva");
    }

    @Test
    void shouldTreatABlankLoginAsAbsent() {
        assertThat(new UserFilter("   ", null).login()).isNull();
        assertThat(new UserFilter(null, null).login()).isNull();
    }

    @Test
    void shouldKeepTheActiveFilter() {
        assertThat(new UserFilter(null, true).active()).isTrue();
        assertThat(new UserFilter(null, false).active()).isFalse();
    }

    @Test
    void shouldKeepTheRoleFilter() {
        assertThat(UserFilter.none().role()).isNull();
        assertThat(new UserFilter(null, null, Role.READER).role()).isEqualTo(Role.READER);
    }
}
