package com.escontrela.lastmove.application.arena;

/** Lifecycle reported by Lichess for an Arena tournament. */
public enum ArenaTournamentStatus {
  CREATED,
  STARTED,
  FINISHED,
  UNKNOWN;

  public boolean isClosed() {
    return this == FINISHED;
  }
}
