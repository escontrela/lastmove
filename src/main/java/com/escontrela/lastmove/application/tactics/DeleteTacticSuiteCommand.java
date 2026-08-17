package com.escontrela.lastmove.application.tactics;

import com.escontrela.lastmove.domain.player.PlayerId;
import com.escontrela.lastmove.domain.tactics.TacticSuiteId;
import java.util.Objects;

/** Deletes one owned tactic suite and all of its exercises. */
public record DeleteTacticSuiteCommand(PlayerId ownerId, TacticSuiteId suiteId) {
  public DeleteTacticSuiteCommand {
    Objects.requireNonNull(ownerId, "ownerId must not be null");
    Objects.requireNonNull(suiteId, "suiteId must not be null");
  }
}
