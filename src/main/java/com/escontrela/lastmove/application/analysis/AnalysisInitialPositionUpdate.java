package com.escontrela.lastmove.application.analysis;

/** Outcome of attempting to replace an analysis session's initial position. */
public record AnalysisInitialPositionUpdate(
    boolean updated, boolean requiresMoveReset, int discardedMoveCount) {

  public AnalysisInitialPositionUpdate {
    if (discardedMoveCount < 0) {
      throw new IllegalArgumentException("discardedMoveCount must not be negative");
    }
    if (updated && requiresMoveReset) {
      throw new IllegalArgumentException("an update cannot still require confirmation");
    }
  }
}