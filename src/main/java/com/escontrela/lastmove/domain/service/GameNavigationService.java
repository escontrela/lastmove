package com.escontrela.lastmove.domain.service;

import com.escontrela.lastmove.domain.game.Move;
import com.escontrela.lastmove.domain.game.MoveTree;
import com.escontrela.lastmove.domain.notation.Fen;

import java.util.Optional;

/**
 * Domain service for navigating a {@link MoveTree}.
 *
 * <p>Provides the basic cursor operations (next, previous, rewind, jump) used by the
 * application layer to drive board and move-list updates.
 */
public class GameNavigationService {

    /**
     * Advances to the next move in the main line.
     *
     * @param tree the move tree to advance
     * @return the move that was entered, or empty if already at the end
     */
    public Optional<Move> next(MoveTree tree) {
        return tree.next();
    }

    /**
     * Steps back to the previous move.
     *
     * @param tree the move tree to step back
     * @return the move now current (i.e. the one before the move that was undone), or empty
     *         if now before the first move
     */
    public Optional<Move> previous(MoveTree tree) {
        return tree.previous();
    }

    /**
     * Rewinds to the initial position (before move 1).
     *
     * @param tree the move tree to rewind
     */
    public void rewind(MoveTree tree) {
        tree.rewind();
    }

    /**
     * Returns the FEN of the position that is currently displayed.
     *
     * @param tree the move tree
     * @param startingFen the FEN before the first move
     * @return the FEN of the current position
     */
    public Fen currentFen(MoveTree tree, Fen startingFen) {
        return tree.currentMove()
                .map(Move::getResultingFen)
                .orElse(startingFen);
    }
}
