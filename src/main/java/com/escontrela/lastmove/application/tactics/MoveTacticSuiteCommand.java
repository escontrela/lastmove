package com.escontrela.lastmove.application.tactics;

import com.escontrela.lastmove.domain.player.PlayerId;
import com.escontrela.lastmove.domain.tactics.TacticSuiteId;
import java.util.Objects;

/** Moves one owned tactic suite by one place in the owner's library order. */
public record MoveTacticSuiteCommand(PlayerId ownerId, TacticSuiteId suiteId, int offset) {
  public MoveTacticSuiteCommand {
    Objects.requireNonNull(ownerId, "ownerId must not be null");
    Objects.requireNonNull(suiteId, "suiteId must not be null");
  }
}
