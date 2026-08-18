package com.escontrela.lastmove.application.tactics;

import com.escontrela.lastmove.domain.analysis.AnalysisSessionId;
import com.escontrela.lastmove.domain.player.PlayerId;
import com.escontrela.lastmove.domain.tactics.TacticSuiteId;
import java.util.Objects;

/** Copies the current analysis position and continuation tree into a tactical exercise. */
public record CopyAnalysisSessionTacticCommand(
    PlayerId ownerId, TacticSuiteId suiteId, AnalysisSessionId sessionId, String title) {
  public CopyAnalysisSessionTacticCommand {
    Objects.requireNonNull(ownerId, "ownerId must not be null");
    Objects.requireNonNull(suiteId, "suiteId must not be null");
    Objects.requireNonNull(sessionId, "sessionId must not be null");
    Objects.requireNonNull(title, "title must not be null");
  }
}
