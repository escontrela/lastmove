package com.escontrela.lastmove.application.tactics;

import com.escontrela.lastmove.domain.player.PlayerId;
import com.escontrela.lastmove.domain.tactics.TacticSuiteId;
import java.util.Objects;

/** Immutable context for authoring one new tactic exercise in the position editor. */
public record TacticPositionEditContext(PlayerId ownerId, TacticSuiteId suiteId) {

  public TacticPositionEditContext {
    Objects.requireNonNull(ownerId, "ownerId must not be null");
    Objects.requireNonNull(suiteId, "suiteId must not be null");
  }
}