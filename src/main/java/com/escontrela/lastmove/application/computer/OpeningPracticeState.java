package com.escontrela.lastmove.application.computer;

/** UI-friendly state of the optional guided opening. */
public enum OpeningPracticeState {
  NOT_CONFIGURED,
  FOLLOWING,
  COMPLETED,
  ABANDONED_BY_DEVIATION,
  ABANDONED_BY_SAFETY_THRESHOLD
}
