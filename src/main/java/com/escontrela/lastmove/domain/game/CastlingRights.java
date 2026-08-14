package com.escontrela.lastmove.domain.game;

/** Immutable castling availability for a chess position. */
public record CastlingRights(
    boolean whiteKingSide, boolean whiteQueenSide, boolean blackKingSide, boolean blackQueenSide) {

  public static CastlingRights none() {
    return new CastlingRights(false, false, false, false);
  }

  public static CastlingRights initial() {
    return new CastlingRights(true, true, true, true);
  }
}
