package com.escontrela.lastmove.application.dto;

import com.escontrela.lastmove.domain.game.Move;
import com.escontrela.lastmove.domain.notation.Fen;

import java.util.Optional;

/**
 * Result returned after a move-navigation step, carrying the new FEN position
 * and the move that was entered (or empty when at the start).
 */
public class MoveNavigationResult {

    private final Fen fen;
    private final Move move;

    private MoveNavigationResult(Fen fen, Move move) {
        this.fen = fen;
        this.move = move;
    }

    public static MoveNavigationResult of(Fen fen, Move move) {
        return new MoveNavigationResult(fen, move);
    }

    public static MoveNavigationResult atStart(Fen fen) {
        return new MoveNavigationResult(fen, null);
    }

    public Fen getFen() {
        return fen;
    }

    public Optional<Move> getMove() {
        return Optional.ofNullable(move);
    }
}
