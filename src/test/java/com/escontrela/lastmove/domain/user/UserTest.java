package com.escontrela.lastmove.domain.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class UserTest {

    @Test
    void unknownUserHasUnknownName() {
        assertEquals("unknown", User.UNKNOWN.name());
    }

    @Test
    void createsNamedUser() {
        User user = User.named("Alice");

        assertEquals("Alice", user.name());
    }

    @Test
    void trimsWhitespace() {
        User user = User.named("  Bob  ");

        assertEquals("Bob", user.name());
    }

    @Test
    void rejectsBlankName() {
        assertThrows(IllegalArgumentException.class, () -> User.named("   "));
    }

    @Test
    void rejectsNullName() {
        assertThrows(NullPointerException.class, () -> User.named(null));
    }
}
