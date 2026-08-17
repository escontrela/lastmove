package com.escontrela.lastmove.ui.event;

import com.escontrela.lastmove.domain.analysis.AnalysisSessionId;
import java.util.Objects;

/** Opens the study picker to archive one ephemeral analysis session as a chapter. */
public record SelectStudyDestinationEvent(AnalysisSessionId sessionId) {

  public SelectStudyDestinationEvent {
    Objects.requireNonNull(sessionId, "sessionId must not be null");
  }
}
