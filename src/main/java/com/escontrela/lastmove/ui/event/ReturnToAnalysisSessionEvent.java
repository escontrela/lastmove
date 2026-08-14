package com.escontrela.lastmove.ui.event;

import com.escontrela.lastmove.domain.analysis.AnalysisSessionId;
import java.util.Objects;
import java.util.Optional;

/** Returns from session management with the selection and status owned by the analysis screen. */
public record ReturnToAnalysisSessionEvent(
    Optional<AnalysisSessionId> activeSessionId, String statusMessage) {

  public ReturnToAnalysisSessionEvent {
    activeSessionId = Objects.requireNonNull(activeSessionId, "activeSessionId must not be null");
    statusMessage = Objects.requireNonNull(statusMessage, "statusMessage must not be null");
  }
}
