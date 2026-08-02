package com.escontrela.lastmove.domain.game;

import com.escontrela.lastmove.domain.notation.Fen;
import com.escontrela.lastmove.domain.notation.PgnGame;

import java.util.Optional;

/**
 * Represents a complete chess game including its metadata, move tree and result.
 *
 * <p>This is the central aggregate root of the domain model.
 */
public class Game {

    private final PgnGame pgnGame;
    private final MoveTree moveTree;
    private GameState state;

    public Game(PgnGame pgnGame, MoveTree moveTree) {
        this.pgnGame = pgnGame;
        this.moveTree = moveTree;
        this.state = GameState.NOT_STARTED;
    }

    public PgnGame getPgnGame() {
        return pgnGame;
    }

    public MoveTree getMoveTree() {
        return moveTree;
    }

    public GameState getState() {
        return state;
    }

    public void setState(GameState state) {
        this.state = state;
    }

    public Optional<GameResult> getResult() {
        return Optional.ofNullable(pgnGame.getResult());
    }

    /** Returns the starting FEN for this game. */
    public Fen getStartingPosition() {
        return pgnGame.getStartingFen().orElse(Fen.startingPosition());
    }
}
