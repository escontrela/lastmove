package com.escontrela.lastmove.application.event;

import com.escontrela.lastmove.domain.game.Move;
import com.escontrela.lastmove.domain.notation.Fen;

import java.util.Optional;

/**
 * Published when the user navigates to a different move, changing the current board position.
 */
public class CurrentMoveChangedEvent {

    private final Fen currentFen;
    private final Move currentMove;

    /**
     * @param currentFen  the FEN of the position now displayed
     * @param currentMove the move just entered, or {@code null} when before the first move
     */
    public CurrentMoveChangedEvent(Fen currentFen, Move currentMove) {
        this.currentFen = currentFen;
        this.currentMove = currentMove;
    }

    public Fen getCurrentFen() {
        return currentFen;
    }

    public Optional<Move> getCurrentMove() {
        return Optional.ofNullable(currentMove);
    }
}
