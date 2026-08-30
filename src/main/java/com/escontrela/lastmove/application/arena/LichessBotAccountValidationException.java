package com.escontrela.lastmove.application.arena;

/** Safe, user-facing validation failure. Messages must never contain an access token. */
public final class LichessBotAccountValidationException extends RuntimeException {
  public LichessBotAccountValidationException(String message) {
    super(message);
  }

  public LichessBotAccountValidationException(String message, Throwable cause) {
    super(message, cause);
  }
}
