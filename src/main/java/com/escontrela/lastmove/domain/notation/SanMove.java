package com.escontrela.lastmove.domain.notation;

import java.util.Objects;

/**
 * An immutable Standard Algebraic Notation (SAN) move string such as {@code "e4"} or
 * {@code "Nf3+"}.
 *
 * <p>SAN is the human-readable notation used in PGN files and chess literature.
 */
public final class SanMove {

    private final String value;

    private SanMove(String value) {
        this.value = Objects.requireNonNull(value, "SAN value must not be null");
    }

    /** Creates a {@link SanMove} from a raw SAN string. */
    public static SanMove of(String san) {
        return new SanMove(san);
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SanMove other)) return false;
        return value.equals(other.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
