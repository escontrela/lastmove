package com.escontrela.lastmove.application.service;

import com.escontrela.lastmove.domain.game.ChessGame;
import com.escontrela.lastmove.domain.game.GameTerminationReason;

/** Applies local automatic results that are not supplied by the rules engine. */
final class LocalGameAdjudication {
  private LocalGameAdjudication() { }

  static boolean drawBareKings(ChessGame game) {
    if (game.result().isPresent() || !game.currentPosition().hasOnlyKings()) return false;
    game.draw(GameTerminationReason.INSUFFICIENT_MATERIAL);
    return true;
  }
}
