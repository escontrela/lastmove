package com.escontrela.lastmove.domain.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * The tree of moves for a chess game, including the main line and all variations.
 *
 * <p>Navigation starts from the root node which represents the position before the first move.
 */
public class MoveTree {

    private final List<Move> mainLine;
    private final List<Variation> variations;
    private int currentIndex;

    public MoveTree() {
        this.mainLine = new ArrayList<>();
        this.variations = new ArrayList<>();
        this.currentIndex = -1;
    }

    public List<Move> getMainLine() {
        return Collections.unmodifiableList(mainLine);
    }

    public List<Variation> getVariations() {
        return Collections.unmodifiableList(variations);
    }

    public void addMove(Move move) {
        mainLine.add(move);
    }

    public void addVariation(Variation variation) {
        variations.add(variation);
    }

    /** Returns the move at the current cursor position, if any. */
    public Optional<Move> currentMove() {
        if (currentIndex < 0 || currentIndex >= mainLine.size()) {
            return Optional.empty();
        }
        return Optional.of(mainLine.get(currentIndex));
    }

    /** Advances the cursor to the next move and returns it. */
    public Optional<Move> next() {
        if (currentIndex + 1 < mainLine.size()) {
            currentIndex++;
            return Optional.of(mainLine.get(currentIndex));
        }
        return Optional.empty();
    }

    /** Moves the cursor back one move and returns it. */
    public Optional<Move> previous() {
        if (currentIndex > 0) {
            currentIndex--;
            return Optional.of(mainLine.get(currentIndex));
        }
        if (currentIndex == 0) {
            currentIndex = -1;
        }
        return Optional.empty();
    }

    /** Resets the cursor to before the first move. */
    public void rewind() {
        currentIndex = -1;
    }

    public int getCurrentIndex() {
        return currentIndex;
    }

    public boolean isAtStart() {
        return currentIndex < 0;
    }

    public boolean isAtEnd() {
        return !mainLine.isEmpty() && currentIndex == mainLine.size() - 1;
    }
}
