package com.escontrela.lastmove.application.tactics;

import java.util.Objects;
import java.util.UUID;

/** Process-local tactic attempt that is deliberately not added to a tactic suite. */
public record TemporaryTacticSession(UUID sessionId, TacticWorkspace workspace) {

  public TemporaryTacticSession {
    Objects.requireNonNull(sessionId, "sessionId must not be null");
    Objects.requireNonNull(workspace, "workspace must not be null");
  }
}
