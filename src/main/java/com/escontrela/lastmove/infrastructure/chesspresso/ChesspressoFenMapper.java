package com.escontrela.lastmove.infrastructure.chesspresso;

import org.chesspresso.position.Position;
import com.escontrela.lastmove.domain.notation.Fen;

/**
 * Converts between LastMove {@link Fen} objects and Chesspresso {@link Position} objects.
 *
 * <p>Chesspresso types must not escape this class into the domain or application layers.
 */
public final class ChesspressoFenMapper {

    private ChesspressoFenMapper() {}

    /**
     * Creates a Chesspresso {@link Position} from a {@link Fen}.
     *
     * @param fen the FEN position to parse
     * @return a Chesspresso position
     * @throws IllegalArgumentException if the FEN is invalid
     */
    public static Position toPosition(Fen fen) {
        return new Position(fen.getValue());
    }

    /**
     * Extracts the FEN string from a Chesspresso {@link Position}.
     *
     * @param position the Chesspresso position
     * @return the corresponding {@link Fen}
     */
    public static Fen toFen(Position position) {
        return Fen.of(position.getFEN());
    }
}
