package com.riverfount.booksmanager.shared.application.port.out;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class NotificationTest {

    @Test
    void shouldRejectBlankFields() {
        assertThatThrownBy(() -> new Notification(" ", "s", "b")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Notification("a@b.c", "", "b")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Notification("a@b.c", "s", null)).isInstanceOf(IllegalArgumentException.class);
    }
}
