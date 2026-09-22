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
    long mateConfirmations,
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
    boolean quietnessMetricsAvailable,
    StopReason stopReason,
    long elapsedMillis) {

  /** Compatibility constructor for snapshots created before quietness support was recorded. */
  public SearchTelemetrySnapshot(
      SearchTelemetryContext context, SearchTelemetryEvent event, java.time.Instant observedAt,
      int depth, Move bestMove, int score, long mainNodes, long qNodes, long ttProbes,
      long ttHits, long ttCutoffs, long betaCutoffs, long pvsResearches, long nullMoveAttempts,
      long nullMoveCutoffs, long lmrApplications, long lmrResearches, long aspirationRetries,
      long evaluationCacheHits, long evaluationCacheMisses, int requestedWorkers,
      int activeWorkers, long quiescenceEntries, long standPatCutoffs, long stalemateChecks,
      long moveListsGenerated, long quietChecksExamined, long seeEvaluations, long seePrunes,
      StopReason stopReason, long elapsedMillis) {
    this(context, event, observedAt, depth, bestMove, score, mainNodes, qNodes, ttProbes,
        ttHits, ttCutoffs, betaCutoffs, pvsResearches, nullMoveAttempts, nullMoveCutoffs,
        lmrApplications, lmrResearches, aspirationRetries, 0, evaluationCacheHits,
        evaluationCacheMisses, requestedWorkers, activeWorkers, quiescenceEntries,
        standPatCutoffs, stalemateChecks, moveListsGenerated, quietChecksExamined,
        seeEvaluations, seePrunes, false, stopReason, elapsedMillis);
  }

  /** Compatibility constructor for snapshots that already declared quietness availability. */
  public SearchTelemetrySnapshot(
      SearchTelemetryContext context, SearchTelemetryEvent event, java.time.Instant observedAt,
      int depth, Move bestMove, int score, long mainNodes, long qNodes, long ttProbes,
      long ttHits, long ttCutoffs, long betaCutoffs, long pvsResearches, long nullMoveAttempts,
      long nullMoveCutoffs, long lmrApplications, long lmrResearches, long aspirationRetries,
      long evaluationCacheHits, long evaluationCacheMisses, int requestedWorkers,
      int activeWorkers, long quiescenceEntries, long standPatCutoffs, long stalemateChecks,
      long moveListsGenerated, long quietChecksExamined, long seeEvaluations, long seePrunes,
      boolean quietnessMetricsAvailable, StopReason stopReason, long elapsedMillis) {
    this(context, event, observedAt, depth, bestMove, score, mainNodes, qNodes, ttProbes,
        ttHits, ttCutoffs, betaCutoffs, pvsResearches, nullMoveAttempts, nullMoveCutoffs,
        lmrApplications, lmrResearches, aspirationRetries, 0, evaluationCacheHits,
        evaluationCacheMisses, requestedWorkers, activeWorkers, quiescenceEntries,
        standPatCutoffs, stalemateChecks, moveListsGenerated, quietChecksExamined,
        seeEvaluations, seePrunes, quietnessMetricsAvailable, stopReason, elapsedMillis);
  }

  public boolean isMateScore() {
    return Math.abs(score) >= 1_000_000 - 128;
  }
}
