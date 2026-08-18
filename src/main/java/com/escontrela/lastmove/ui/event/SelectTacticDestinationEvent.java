package com.escontrela.lastmove.ui.event;

import com.escontrela.lastmove.domain.analysis.AnalysisSessionId;
import java.util.Objects;

/** Opens the tactic library so an analysis position can be copied into a selected suite. */
public record SelectTacticDestinationEvent(AnalysisSessionId sessionId) {
  public SelectTacticDestinationEvent {
    Objects.requireNonNull(sessionId, "sessionId must not be null");
  }
}
