package com.escontrela.lastmove.application.event;

import com.escontrela.lastmove.domain.game.Game;

/**
 * Published when a PGN game has been successfully loaded into the application.
 */
public class GameLoadedEvent {

    private final Game game;

    public GameLoadedEvent(Game game) {
        this.game = game;
    }

    public Game getGame() {
        return game;
    }
}
