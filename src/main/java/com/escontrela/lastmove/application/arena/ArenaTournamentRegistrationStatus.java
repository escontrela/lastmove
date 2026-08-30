package com.escontrela.lastmove.application.arena;

/** Locally observable participation state for Knight Shade in one Arena tournament. */
public enum ArenaTournamentRegistrationStatus {
  AVAILABLE,
  JOINING,
  JOINED,
  NOT_ELIGIBLE,
  CLOSED,
  INCOMPATIBLE,
  ERROR;

  /** Guards the state machine used by the later join workflow. */
  public boolean canTransitionTo(ArenaTournamentRegistrationStatus next) {
    if (this == next) return true;
    return switch (this) {
      case AVAILABLE -> next == JOINING || next == NOT_ELIGIBLE || next == CLOSED
          || next == INCOMPATIBLE || next == ERROR;
      case JOINING -> next == JOINED || next == AVAILABLE || next == NOT_ELIGIBLE
          || next == CLOSED || next == ERROR;
      case JOINED -> next == CLOSED || next == ERROR;
      case NOT_ELIGIBLE -> next == AVAILABLE || next == CLOSED || next == ERROR;
      case ERROR -> next == AVAILABLE || next == JOINING || next == CLOSED || next == NOT_ELIGIBLE;
      case INCOMPATIBLE, CLOSED -> false;
    };
  }
}
