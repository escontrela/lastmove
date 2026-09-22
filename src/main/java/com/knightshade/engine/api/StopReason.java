package com.knightshade.engine.api;

/** Why a Knightshade search stopped. */
public enum StopReason {
  COMPLETED,
  DEPTH_LIMIT,
  MATE,
  TIME_LIMIT,
  CANCELLED,
  NO_LEGAL_MOVE,
  ERROR
}
