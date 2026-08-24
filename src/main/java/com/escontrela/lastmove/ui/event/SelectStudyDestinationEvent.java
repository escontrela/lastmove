package com.escontrela.lastmove.ui.event;

import com.escontrela.lastmove.domain.analysis.AnalysisSessionId;
import java.util.Objects;

/** Opens the study picker to archive one ephemeral analysis session as a chapter. */
public record SelectStudyDestinationEvent(
    AnalysisSessionId sessionId, PostCopyDestination postCopyDestination) {

  public SelectStudyDestinationEvent(AnalysisSessionId sessionId) {
    this(sessionId, PostCopyDestination.RETURN_TO_ANALYSIS);
  }

  public SelectStudyDestinationEvent {
    Objects.requireNonNull(sessionId, "sessionId must not be null");
    Objects.requireNonNull(postCopyDestination, "postCopyDestination must not be null");
  }

  /** Selects the screen displayed after the chapter has been persisted. */
  public enum PostCopyDestination {
    RETURN_TO_ANALYSIS,
    OPEN_STUDY_WORKSPACE
  }
}
