package com.escontrela.lastmove.infrastructure.persistence;

import java.util.Objects;
import java.util.Optional;

/**
 * Reflects whether the local SQLite database is available.
 *
 * <p>The application intentionally starts even when migrations fail; this value object lets the UI
 * show a degraded state instead of crashing.
 */
public final class PersistenceAvailability {

    public enum Status {
        AVAILABLE,
        UNAVAILABLE
    }

    private final Status status;
    private final String reason;

    private PersistenceAvailability(Status status, String reason) {
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.reason = reason;
    }

    public static PersistenceAvailability available() {
        return new PersistenceAvailability(Status.AVAILABLE, null);
    }

    public static PersistenceAvailability unavailable(String reason) {
        return new PersistenceAvailability(Status.UNAVAILABLE, reason);
    }

    public boolean isAvailable() {
        return status == Status.AVAILABLE;
    }

    public Optional<String> reason() {
        return Optional.ofNullable(reason);
    }

    @Override
    public String toString() {
        return status + (reason == null ? "" : ": " + reason);
    }
}
