package com.escontrela.lastmove.application.arena;

/** A safe, transport-neutral failure while requesting the Lichess tournament schedule. */
public final class LichessTournamentRequestException extends RuntimeException {
  public enum Kind { UNAUTHORIZED, FORBIDDEN, RATE_LIMITED, TRANSPORT, INVALID_RESPONSE, UNEXPECTED_RESPONSE }

  private final Kind kind;

  public LichessTournamentRequestException(Kind kind, String message) {
    super(message);
    this.kind = kind;
  }

  public LichessTournamentRequestException(Kind kind, String message, Throwable cause) {
    super(message, cause);
    this.kind = kind;
  }

  public Kind kind() { return kind; }
}
