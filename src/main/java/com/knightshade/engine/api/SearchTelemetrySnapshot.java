package com.knightshade.engine.api;

import com.knightshade.engine.board.Move;

/** Immutable, engine-neutral diagnostics for one completed depth or search. */
public record SearchTelemetrySnapshot(
    SearchTelemetryContext context,
    SearchTelemetryEvent event,
    java.time.Instant observedAt,
    int depth,
    Move bestMove,
    int score,
    long mainNodes,
    long qNodes,
    long ttProbes,
    long ttHits,
    long ttCutoffs,
    long betaCutoffs,
    long pvsResearches,
    long nullMoveAttempts,
    long nullMoveCutoffs,
    long lmrApplications,
    long lmrResearches,
    long aspirationRetries,
    long evaluationCacheHits,
    long evaluationCacheMisses,
    int requestedWorkers,
    int activeWorkers,
    long quiescenceEntries,
    long standPatCutoffs,
    long stalemateChecks,
    long moveListsGenerated,
    long quietChecksExamined,
    long seeEvaluations,
    long seePrunes,
    StopReason stopReason,
    long elapsedMillis) {

  public boolean isMateScore() {
    return Math.abs(score) >= 1_000_000 - 128;
  }
}
