package com.escontrela.lastmove.domain.notation;

import com.escontrela.lastmove.domain.common.ChessConstants;

import java.util.Objects;

/**
 * An immutable Forsyth-Edwards Notation (FEN) string representing a board position.
 *
 * <p>FEN encodes the piece placement, active color, castling availability,
 * en-passant target square, half-move clock, and full-move number.
 */
public final class Fen {

    private final String value;

    private Fen(String value) {
        this.value = Objects.requireNonNull(value, "FEN value must not be null");
    }

    /** Creates a {@link Fen} from a raw FEN string without validation. */
    public static Fen of(String fen) {
        return new Fen(fen);
    }

    /** Returns the FEN for the standard chess starting position. */
    public static Fen startingPosition() {
        return new Fen(ChessConstants.STARTING_FEN);
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Fen other)) return false;
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
