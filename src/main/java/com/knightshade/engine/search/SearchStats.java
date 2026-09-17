package com.knightshade.engine.search;

import com.knightshade.engine.api.SearchTelemetrySnapshot;
import com.knightshade.engine.api.StopReason;
import com.knightshade.engine.board.Move;

/** Mutable per-search/per-worker counters. Never shared by search workers. */
final class SearchStats {
  long mainNodes, qNodes, ttProbes, ttHits, ttCutoffs, betaCutoffs, pvsResearches;
  long nullMoveAttempts, nullMoveCutoffs, lmrApplications, lmrResearches, aspirationRetries;
  long evaluationCacheHits, evaluationCacheMisses;

  void merge(SearchStats other) {
    mainNodes += other.mainNodes; qNodes += other.qNodes; ttProbes += other.ttProbes;
    ttHits += other.ttHits; ttCutoffs += other.ttCutoffs; betaCutoffs += other.betaCutoffs;
    pvsResearches += other.pvsResearches; nullMoveAttempts += other.nullMoveAttempts;
    nullMoveCutoffs += other.nullMoveCutoffs; lmrApplications += other.lmrApplications;
    lmrResearches += other.lmrResearches; aspirationRetries += other.aspirationRetries;
    evaluationCacheHits += other.evaluationCacheHits; evaluationCacheMisses += other.evaluationCacheMisses;
  }

  SearchTelemetrySnapshot snapshot(int depth, Move move, int score, int requested, int effective,
      StopReason reason, long elapsedMillis) {
    return new SearchTelemetrySnapshot(depth, move, score, mainNodes, qNodes, ttProbes, ttHits,
        ttCutoffs, betaCutoffs, pvsResearches, nullMoveAttempts, nullMoveCutoffs, lmrApplications,
        lmrResearches, aspirationRetries, evaluationCacheHits, evaluationCacheMisses,
        requested, effective, reason, elapsedMillis);
  }
}
