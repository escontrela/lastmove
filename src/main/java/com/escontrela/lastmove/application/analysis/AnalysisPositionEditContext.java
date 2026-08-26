package com.escontrela.lastmove.application.analysis;

import com.escontrela.lastmove.domain.analysis.AnalysisSessionId;
import com.escontrela.lastmove.domain.game.PositionSnapshot;
import java.util.Objects;

/** Immutable context for editing one analysis session's initial position. */
public record AnalysisPositionEditContext(
    AnalysisSessionId sessionId, PositionSnapshot position) {

  public AnalysisPositionEditContext {
    Objects.requireNonNull(sessionId, "sessionId must not be null");
    Objects.requireNonNull(position, "position must not be null");
  }
}