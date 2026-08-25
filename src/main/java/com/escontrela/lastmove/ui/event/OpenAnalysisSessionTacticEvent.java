package com.escontrela.lastmove.ui.event;

import com.escontrela.lastmove.domain.analysis.AnalysisSessionId;
import java.util.Objects;

/** Requests a retained analysis session to be run as a one-off, non-persisted tactic. */
public record OpenAnalysisSessionTacticEvent(AnalysisSessionId sessionId) {

  public OpenAnalysisSessionTacticEvent {
    Objects.requireNonNull(sessionId, "sessionId must not be null");
  }
}
