package com.escontrela.lastmove.application.tactics;

import com.escontrela.lastmove.domain.player.PlayerId;
import com.escontrela.lastmove.domain.tactics.TacticSuiteId;
import java.util.Objects;

/** Renames one owned tactic suite. */
public record RenameTacticSuiteCommand(PlayerId ownerId, TacticSuiteId suiteId, String title) {
  public RenameTacticSuiteCommand {
    Objects.requireNonNull(ownerId, "ownerId must not be null");
    Objects.requireNonNull(suiteId, "suiteId must not be null");
    Objects.requireNonNull(title, "title must not be null");
  }
}
