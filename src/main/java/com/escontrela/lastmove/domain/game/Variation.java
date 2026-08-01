package com.escontrela.lastmove.domain.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * An alternative line of play that branches off from a specific move in the main line.
 *
 * <p>Variations are referenced by the move index in the parent {@link MoveTree} at which they
 * begin.
 */
public class Variation {

    private final int branchIndex;
    private final List<Move> moves;

    public Variation(int branchIndex) {
        this.branchIndex = branchIndex;
        this.moves = new ArrayList<>();
    }

    public int getBranchIndex() {
        return branchIndex;
    }

    public void addMove(Move move) {
        moves.add(Objects.requireNonNull(move));
    }

    public List<Move> getMoves() {
        return Collections.unmodifiableList(moves);
    }

    public boolean isEmpty() {
        return moves.isEmpty();
    }
}
