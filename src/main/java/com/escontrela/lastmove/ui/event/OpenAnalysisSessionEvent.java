package com.escontrela.lastmove.ui.event;

import com.escontrela.lastmove.domain.analysis.AnalysisSessionId;
import java.util.Objects;

/** Selects a retained analysis session before navigating to the analysis workspace. */
public record OpenAnalysisSessionEvent(AnalysisSessionId sessionId, String statusMessage) {

  public OpenAnalysisSessionEvent {
    Objects.requireNonNull(sessionId, "sessionId must not be null");
    statusMessage = Objects.requireNonNull(statusMessage, "statusMessage must not be null");
  }
}
