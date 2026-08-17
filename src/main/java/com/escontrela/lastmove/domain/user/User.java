package com.escontrela.lastmove.domain.user;

import java.util.Objects;

/**
 * Represents the current application user.
 *
 * <p>This is distinct from {@link com.escontrela.lastmove.domain.player.Player}: a {@code Player}
 * is a persisted profile that can be selected, while {@code User} is the actor currently using the
 * application. When no player has been selected, the user defaults to {@link #UNKNOWN}.
 */
public final class User {

    public static final User UNKNOWN = new User("unknown");

    private final String name;

    private User(String name) {
        this.name = name;
    }

    /** Creates a user with the given display name. */
    public static User named(String name) {
        Objects.requireNonNull(name, "name must not be null");
        String trimmed = name.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        return new User(trimmed);
    }

    public String name() {
        return name;
    }

    @Override
    public String toString() {
        return "User{" + name + "}";
    }
}
