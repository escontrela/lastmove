package com.escontrela.lastmove.application.arena;

/** UI contract for the Tournament list before a JavaFX screen owns its rendering. */
public enum ArenaTournamentListState {
  DISCONNECTED,
  LOADING,
  READY,
  EMPTY,
  ERROR
}
