package com.escontrela.lastmove.application.computer;

/** Lifecycle of one per-game anticipatory search request. */
public enum PonderState {
  IDLE,
  PREDICTING,
  PONDERING,
  READY,
  HIT,
  MISS,
  CANCELLED
}
