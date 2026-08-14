package com.escontrela.lastmove.ui.event;

import com.escontrela.lastmove.domain.analysis.AnalysisSessionId;
import java.util.Objects;

/** Requests the dedicated session-management screen for one analysis workspace. */
public record OpenSessionManagementEvent(AnalysisSessionId activeSessionId) {

  public OpenSessionManagementEvent {
    Objects.requireNonNull(activeSessionId, "activeSessionId must not be null");
  }
}
