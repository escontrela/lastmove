package com.escontrela.lastmove.domain.game;

import com.escontrela.lastmove.domain.common.Square;
import com.escontrela.lastmove.domain.notation.Fen;
import com.escontrela.lastmove.domain.notation.SanMove;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class MoveTreeTest {

    @Test
    void emptyTree_isAtStart() {
        MoveTree tree = new MoveTree();
        assertTrue(tree.isAtStart());
        assertFalse(tree.isAtEnd());
        assertEquals(Optional.empty(), tree.currentMove());
    }

    @Test
    void next_advancesToFirstMove() {
        MoveTree tree = new MoveTree();
        Move move = sampleMove(1);
        tree.addMove(move);

        Optional<Move> result = tree.next();
        assertTrue(result.isPresent());
        assertSame(move, result.get());
        assertTrue(tree.isAtEnd());
    }

    @Test
    void previous_fromFirstMove_returnsEmpty_andResetsToStart() {
        MoveTree tree = new MoveTree();
        tree.addMove(sampleMove(1));
        tree.next();

        Optional<Move> back = tree.previous();
        assertTrue(back.isEmpty());
        assertTrue(tree.isAtStart());
    }

    @Test
    void rewind_resetsToBeforeFirstMove() {
        MoveTree tree = new MoveTree();
        tree.addMove(sampleMove(1));
        tree.addMove(sampleMove(2));
        tree.next();
        tree.next();

        tree.rewind();
        assertTrue(tree.isAtStart());
    }

    private Move sampleMove(int number) {
        return new Move(
                Square.of("e2"),
                Square.of("e4"),
                SanMove.of("e4"),
                Fen.startingPosition(),
                number);
    }
}
