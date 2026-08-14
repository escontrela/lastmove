package com.escontrela.lastmove.domain.game;

/** Lifecycle of a request to rectify moves in a progressive chess game. */
public enum TakebackStatus {
  PENDING,
  ACCEPTED,
  REJECTED,
  APPLIED
}
