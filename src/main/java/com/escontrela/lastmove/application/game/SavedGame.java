package com.escontrela.lastmove.application.game;

import com.escontrela.lastmove.domain.game.ChessGame;
import java.util.Objects;

/** Fully rehydrated saved game, loaded only when a game is opened. */
public record SavedGame(ChessGame game, SavedGameContext context) {
  public SavedGame {
    game = Objects.requireNonNull(game, "game must not be null");
    context = Objects.requireNonNull(context, "context must not be null");
  }
}
