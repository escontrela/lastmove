package com.escontrela.lastmove.domain.service;

import com.escontrela.lastmove.domain.notation.Fen;
import com.escontrela.lastmove.domain.common.PieceColor;
import com.escontrela.lastmove.domain.common.PieceType;
import com.escontrela.lastmove.domain.game.PositionPiece;
import com.escontrela.lastmove.domain.game.PositionSnapshot;
import java.util.Objects;

/**
 * Domain service for working with FEN strings.
 *
 * <p>Provides utility methods for validating, parsing, and converting FEN positions. Does not
 * depend on any third-party chess library.
 */
public class FenService {

  /** Returns the standard starting-position FEN. */
  public Fen startingPosition() {
    return Fen.startingPosition();
  }

  /**
   * Returns {@code true} if the given string has the basic structural form of a FEN. This is a
   * lightweight check and does not validate chess legality.
   *
   * @param fen the FEN string to check
   */
  public boolean isWellFormed(String fen) {
    if (fen == null || fen.isBlank()) return false;
    String[] parts = fen.split("\\s+");
    return parts.length >= 4;
  }

  /**
   * Extracts the active color field from a FEN string.
   *
   * @param fen a well-formed FEN string
   * @return {@code "w"} or {@code "b"}
   */
  public String activeColor(Fen fen) {
    String[] parts = fen.getValue().split("\\s+");
    if (parts.length < 2) throw new IllegalArgumentException("Invalid FEN: " + fen);
    return parts[1];
  }

  /**
   * Serializes a complete engine-neutral position snapshot as FEN.
   *
   * @param snapshot the position currently represented by a game or analysis session
   * @return every board and rules-state field encoded as a FEN value
   */
  public Fen fromSnapshot(PositionSnapshot snapshot) {
    PositionSnapshot required =
        Objects.requireNonNull(snapshot, "snapshot must not be null");
    PositionPiece[][] board = new PositionPiece[8][8];
    for (PositionPiece piece : required.pieces()) {
      int file = piece.square().getFile();
      int rank = piece.square().getRank();
      if (board[rank][file] != null) {
        throw new IllegalArgumentException("More than one piece occupies " + piece.square());
      }
      board[rank][file] = piece;
    }

    StringBuilder fen = new StringBuilder();
    for (int rank = 7; rank >= 0; rank--) {
      int emptySquares = 0;
      for (int file = 0; file < 8; file++) {
        PositionPiece piece = board[rank][file];
        if (piece == null) {
          emptySquares++;
          continue;
        }
        if (emptySquares > 0) {
          fen.append(emptySquares);
          emptySquares = 0;
        }
        fen.append(pieceSymbol(piece));
      }
      if (emptySquares > 0) {
        fen.append(emptySquares);
      }
      if (rank > 0) {
        fen.append('/');
      }
    }

    fen.append(required.activeColor() == PieceColor.WHITE ? " w " : " b ");
    appendCastlingRights(fen, required);
    fen.append(' ')
        .append(required.enPassantTarget().map(square -> square.toAlgebraic()).orElse("-"))
        .append(' ')
        .append(required.halfmoveClock())
        .append(' ')
        .append(required.fullmoveNumber());
    return Fen.of(fen.toString());
  }

  private char pieceSymbol(PositionPiece piece) {
    char symbol =
        switch (piece.type()) {
          case KING -> 'k';
          case QUEEN -> 'q';
          case ROOK -> 'r';
          case BISHOP -> 'b';
          case KNIGHT -> 'n';
          case PAWN -> 'p';
        };
    return piece.color() == PieceColor.WHITE ? Character.toUpperCase(symbol) : symbol;
  }

  private void appendCastlingRights(StringBuilder fen, PositionSnapshot snapshot) {
    var rights = snapshot.castlingRights();
    int start = fen.length();
    if (rights.whiteKingSide()) fen.append('K');
    if (rights.whiteQueenSide()) fen.append('Q');
    if (rights.blackKingSide()) fen.append('k');
    if (rights.blackQueenSide()) fen.append('q');
    if (fen.length() == start) fen.append('-');
  }
}
