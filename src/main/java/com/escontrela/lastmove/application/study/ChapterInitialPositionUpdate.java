package com.escontrela.lastmove.application.study;

/** Outcome of attempting to replace a chapter's initial position. */
public record ChapterInitialPositionUpdate(
    boolean updated, boolean requiresMoveReset, int discardedMoveCount) {

  public ChapterInitialPositionUpdate {
    if (discardedMoveCount < 0) {
      throw new IllegalArgumentException("discardedMoveCount must not be negative");
    }
    if (updated && requiresMoveReset) {
      throw new IllegalArgumentException("an update cannot still require confirmation");
    }
  }
}
