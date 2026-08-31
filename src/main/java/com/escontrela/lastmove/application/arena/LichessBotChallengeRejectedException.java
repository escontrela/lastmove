package com.escontrela.lastmove.application.arena;

/** A remote bot rejected a challenge request; the cycle can safely continue with another bot. */
public final class LichessBotChallengeRejectedException extends IllegalStateException {
  public LichessBotChallengeRejectedException(String message) { super(message); }
}
