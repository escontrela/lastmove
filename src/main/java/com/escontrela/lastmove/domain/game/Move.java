package com.escontrela.lastmove.domain.game;

import com.escontrela.lastmove.domain.common.Square;
import com.escontrela.lastmove.domain.notation.Fen;
import com.escontrela.lastmove.domain.notation.SanMove;

import java.util.Objects;

/**
 * A single move in a chess game.
 *
 * <p>Captures the origin square, destination square, SAN notation, and the resulting FEN
 * position after the move.
 */
public class Move {

    private final Square from;
    private final Square to;
    private final SanMove san;
    private final Fen resultingFen;
    private final int moveNumber;

    public Move(Square from, Square to, SanMove san, Fen resultingFen, int moveNumber) {
        this.from = Objects.requireNonNull(from, "from must not be null");
        this.to = Objects.requireNonNull(to, "to must not be null");
        this.san = Objects.requireNonNull(san, "san must not be null");
        this.resultingFen = Objects.requireNonNull(resultingFen, "resultingFen must not be null");
        this.moveNumber = moveNumber;
    }

    public Square getFrom() {
        return from;
    }

    public Square getTo() {
        return to;
    }

    public SanMove getSan() {
        return san;
    }

    public Fen getResultingFen() {
        return resultingFen;
    }

    public int getMoveNumber() {
        return moveNumber;
    }

    @Override
    public String toString() {
        return moveNumber + ". " + san.getValue();
    }
}
