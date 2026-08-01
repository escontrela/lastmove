package com.escontrela.lastmove.application.dto;

import com.escontrela.lastmove.domain.game.Game;

/**
 * Result returned by {@link com.escontrela.lastmove.application.service.GameLoadService}
 * after attempting to load a PGN game.
 */
public class GameLoadResult {

    private final Game game;
    private final boolean success;
    private final String errorMessage;

    private GameLoadResult(Game game, boolean success, String errorMessage) {
        this.game = game;
        this.success = success;
        this.errorMessage = errorMessage;
    }

    public static GameLoadResult success(Game game) {
        return new GameLoadResult(game, true, null);
    }

    public static GameLoadResult failure(String errorMessage) {
        return new GameLoadResult(null, false, errorMessage);
    }

    public boolean isSuccess() {
        return success;
    }

    public java.util.Optional<Game> getGame() {
        return java.util.Optional.ofNullable(game);
    }

    public java.util.Optional<String> getErrorMessage() {
        return java.util.Optional.ofNullable(errorMessage);
    }
}
