package com.escontrela.lastmove.application.dto;

import com.escontrela.lastmove.domain.analysis.AnalysisOrigin;
import com.escontrela.lastmove.domain.analysis.AnalysisSessionId;
import com.escontrela.lastmove.domain.game.GameResult;
import com.escontrela.lastmove.domain.game.PositionSnapshot;
import java.util.Objects;
import java.util.Optional;

/** Immutable session-list item that does not expose the mutable analysis aggregate. */
public record AnalysisSessionSummary(
    AnalysisSessionId sessionId,
    String title,
    AnalysisOrigin origin,
    PositionSnapshot currentPosition,
    Optional<GameResult> sourceResult) {

  public AnalysisSessionSummary {
    Objects.requireNonNull(sessionId, "sessionId must not be null");
    title = Objects.requireNonNull(title, "title must not be null");
    Objects.requireNonNull(origin, "origin must not be null");
    Objects.requireNonNull(currentPosition, "currentPosition must not be null");
    sourceResult = Objects.requireNonNull(sourceResult, "sourceResult must not be null");
  }
}
