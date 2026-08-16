package com.escontrela.lastmove.domain.player;

import java.util.Objects;

/** Stable identity of a persisted player profile. */
public record PlayerId(Long value) {

    public static PlayerId of(Long value) {
        return new PlayerId(value);
    }

    @Override
    public boolean equals(Object object) {
        if (object == this) {
            return true;
        }
        if (!(object instanceof PlayerId other)) {
            return false;
        }
        return Objects.equals(value, other.value);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }

    @Override
    public String toString() {
        return value == null ? "PlayerId{unassigned}" : "PlayerId{" + value + "}";
    }
}
