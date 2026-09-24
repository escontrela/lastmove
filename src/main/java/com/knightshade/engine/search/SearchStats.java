package com.knightshade.engine.search;

import com.knightshade.engine.api.SearchTelemetrySnapshot;
import com.knightshade.engine.api.StopReason;
import com.knightshade.engine.board.Move;

/** Mutable per-search/per-worker counters. Never shared by search workers. */
final class SearchStats {
  long mainNodes, qNodes, ttProbes, ttHits, ttCutoffs, betaCutoffs, pvsResearches;
  long nullMoveAttempts, nullMoveCutoffs, lmrApplications, lmrResearches, aspirationRetries;
  long mateConfirmations;
  long evaluationCacheHits, evaluationCacheMisses;
  long quiescenceEntries, standPatCutoffs, stalemateChecks, moveListsGenerated;
  long quietChecksExamined, seeEvaluations, seePrunes;
  boolean quietnessMetricsAvailable;

  void merge(SearchStats other) {
    mainNodes += other.mainNodes; qNodes += other.qNodes; ttProbes += other.ttProbes;
    ttHits += other.ttHits; ttCutoffs += other.ttCutoffs; betaCutoffs += other.betaCutoffs;
    pvsResearches += other.pvsResearches; nullMoveAttempts += other.nullMoveAttempts;
    nullMoveCutoffs += other.nullMoveCutoffs; lmrApplications += other.lmrApplications;
    lmrResearches += other.lmrResearches; aspirationRetries += other.aspirationRetries;
    evaluationCacheHits += other.evaluationCacheHits; evaluationCacheMisses += other.evaluationCacheMisses;
    quiescenceEntries += other.quiescenceEntries; standPatCutoffs += other.standPatCutoffs;
    stalemateChecks += other.stalemateChecks; moveListsGenerated += other.moveListsGenerated;
    quietChecksExamined += other.quietChecksExamined; seeEvaluations += other.seeEvaluations;
    seePrunes += other.seePrunes;
    mateConfirmations += other.mateConfirmations;
    quietnessMetricsAvailable |= other.quietnessMetricsAvailable;
  }

  SearchTelemetrySnapshot snapshot(com.knightshade.engine.api.SearchTelemetryContext context,
      com.knightshade.engine.api.SearchTelemetryEvent event, int depth, Move move, int score,
      int requested, int active, StopReason reason, long elapsedMillis) {
    return new SearchTelemetrySnapshot(context, event, java.time.Instant.now(), depth, move, score, mainNodes, qNodes, ttProbes, ttHits,
        ttCutoffs, betaCutoffs, pvsResearches, nullMoveAttempts, nullMoveCutoffs, lmrApplications,
        lmrResearches, aspirationRetries, mateConfirmations, evaluationCacheHits, evaluationCacheMisses,
        requested, active, quiescenceEntries, standPatCutoffs, stalemateChecks, moveListsGenerated,
        quietChecksExamined, seeEvaluations, seePrunes, quietnessMetricsAvailable,
        reason, elapsedMillis, 0);
  }
}
