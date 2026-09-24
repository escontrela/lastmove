package com.knightshade.engine.api;

import com.knightshade.engine.board.Move;

/** Immutable, engine-neutral diagnostics for a completed depth, search, or ponder decision. */
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
    long elapsedMillis,
    int ponderReusedDepth) {

  /** Creates the single, non-search sample emitted when an actual reply validates a prediction. */
  public static SearchTelemetrySnapshot ponderDecision(SearchTelemetryContext context,
      SearchTelemetryEvent event, int reusedDepth) {
    if (event != SearchTelemetryEvent.PONDER_HIT && event != SearchTelemetryEvent.PONDER_MISS) {
      throw new IllegalArgumentException("event must describe a ponder decision");
    }
    return ponderEvent(context, event, reusedDepth);
  }

  public static SearchTelemetrySnapshot ponderStarted(SearchTelemetryContext context) {
    return ponderEvent(context, SearchTelemetryEvent.PONDER_STARTED, 0);
  }

  private static SearchTelemetrySnapshot ponderEvent(SearchTelemetryContext context,
      SearchTelemetryEvent event, int reusedDepth) {
    return new SearchTelemetrySnapshot(context, event, java.time.Instant.now(), 0, null, 0,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, false, null, 0, reusedDepth);
  }

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
        seeEvaluations, seePrunes, false, stopReason, elapsedMillis, 0);
  }

  /** Compatibility constructor for the previous canonical shape, including mate/quietness data. */
  public SearchTelemetrySnapshot(
      SearchTelemetryContext context, SearchTelemetryEvent event, java.time.Instant observedAt,
      int depth, Move bestMove, int score, long mainNodes, long qNodes, long ttProbes,
      long ttHits, long ttCutoffs, long betaCutoffs, long pvsResearches, long nullMoveAttempts,
      long nullMoveCutoffs, long lmrApplications, long lmrResearches, long aspirationRetries,
      long mateConfirmations, long evaluationCacheHits, long evaluationCacheMisses,
      int requestedWorkers, int activeWorkers, long quiescenceEntries, long standPatCutoffs,
      long stalemateChecks, long moveListsGenerated, long quietChecksExamined,
      long seeEvaluations, long seePrunes, boolean quietnessMetricsAvailable,
      StopReason stopReason, long elapsedMillis) {
    this(context, event, observedAt, depth, bestMove, score, mainNodes, qNodes, ttProbes,
        ttHits, ttCutoffs, betaCutoffs, pvsResearches, nullMoveAttempts, nullMoveCutoffs,
        lmrApplications, lmrResearches, aspirationRetries, mateConfirmations,
        evaluationCacheHits, evaluationCacheMisses, requestedWorkers, activeWorkers,
        quiescenceEntries, standPatCutoffs, stalemateChecks, moveListsGenerated,
        quietChecksExamined, seeEvaluations, seePrunes, quietnessMetricsAvailable,
        stopReason, elapsedMillis, 0);
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
        seeEvaluations, seePrunes, quietnessMetricsAvailable, stopReason, elapsedMillis, 0);
  }

  public boolean isMateScore() {
    return Math.abs(score) >= 1_000_000 - 128;
  }
}
