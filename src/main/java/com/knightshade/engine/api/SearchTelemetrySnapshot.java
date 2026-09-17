package com.knightshade.engine.api;

import com.knightshade.engine.board.Move;

/** Immutable, engine-neutral diagnostics for one completed depth or search. */
public record SearchTelemetrySnapshot(
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
    int effectiveWorkers,
    StopReason stopReason,
    long elapsedMillis) {}
