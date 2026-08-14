package com.escontrela.lastmove.infrastructure.chesspresso;

import chesspresso.Chess;
import chesspresso.position.ImmutablePosition;
import chesspresso.position.Position;
import com.escontrela.lastmove.domain.common.PieceColor;
import com.escontrela.lastmove.domain.common.PieceType;
import com.escontrela.lastmove.domain.common.Square;
import com.escontrela.lastmove.domain.game.CastlingRights;
import com.escontrela.lastmove.domain.game.MoveDescriptor;
import com.escontrela.lastmove.domain.game.PositionPiece;
import com.escontrela.lastmove.domain.game.PositionSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Maps complete engine-neutral {@link PositionSnapshot} values to and from Chesspresso positions.
 *
 * <p>This is the only translation point used by move validation. Chesspresso objects never leave
 * the infrastructure package.
 */
final class ChesspressoPositionSnapshotMapper {

  private ChesspressoPositionSnapshotMapper() {}

  static Position toPosition(PositionSnapshot snapshot) {
    Position position = new Position();
    position.clear();
    for (PositionPiece piece : snapshot.pieces()) {
      position.setStone(
          Chess.coorToSqi(piece.square().getFile(), piece.square().getRank()),
          Chess.pieceToStone(toChesspressoPiece(piece.type()), toChesspressoColor(piece.color())));
    }
    position.setToPlay(toChesspressoColor(snapshot.activeColor()));
    position.setCastles(toChesspressoCastles(snapshot.castlingRights()));
    position.setSqiEP(
        snapshot.enPassantTarget()
            .map(square -> Chess.coorToSqi(square.getFile(), square.getRank()))
            .orElse(Chess.NO_SQUARE));
    position.setHalfMoveClock(snapshot.halfmoveClock());
    position.setPlyNumber(toPlyNumber(snapshot.fullmoveNumber(), snapshot.activeColor()));
    return position;
  }

  static PositionSnapshot fromPosition(Position position, Optional<MoveDescriptor> lastMove) {
    List<PositionPiece> pieces = new ArrayList<>();
    for (int squareIndex = 0; squareIndex < Chess.NUM_OF_SQUARES; squareIndex++) {
      int stone = position.getStone(squareIndex);
      if (stone != Chess.NO_STONE) {
        pieces.add(
            new PositionPiece(
                Square.of(Chess.sqiToCol(squareIndex), Chess.sqiToRow(squareIndex)),
                toPieceType(Chess.stoneToPiece(stone)),
                Chess.stoneToColor(stone) == Chess.WHITE ? PieceColor.WHITE : PieceColor.BLACK));
      }
    }
    return new PositionSnapshot(
        pieces,
        position.getToPlay() == Chess.WHITE ? PieceColor.WHITE : PieceColor.BLACK,
        fromChesspressoCastles(position.getCastles()),
        position.getSqiEP() == Chess.NO_SQUARE
            ? Optional.empty()
            : Optional.of(
                Square.of(Chess.sqiToCol(position.getSqiEP()), Chess.sqiToRow(position.getSqiEP()))),
        position.getHalfMoveClock(),
        Chess.plyToMoveNumber(position.getPlyNumber()),
        lastMove,
        position.isCheck(),
        position.isMate(),
        position.isStaleMate());
  }

  static PositionPiece pieceAt(Position position, int squareIndex) {
    int stone = position.getStone(squareIndex);
    if (stone == Chess.NO_STONE) {
      return null;
    }
    return new PositionPiece(
        Square.of(Chess.sqiToCol(squareIndex), Chess.sqiToRow(squareIndex)),
        toPieceType(Chess.stoneToPiece(stone)),
        Chess.stoneToColor(stone) == Chess.WHITE ? PieceColor.WHITE : PieceColor.BLACK);
  }

  private static int toPlyNumber(int fullmoveNumber, PieceColor activeColor) {
    return (fullmoveNumber - 1) * 2 + (activeColor == PieceColor.BLACK ? 1 : 0);
  }

  private static int toChesspressoCastles(CastlingRights rights) {
    int castles = ImmutablePosition.NO_CASTLES;
    if (rights.whiteQueenSide()) castles |= ImmutablePosition.WHITE_LONG_CASTLE;
    if (rights.whiteKingSide()) castles |= ImmutablePosition.WHITE_SHORT_CASTLE;
    if (rights.blackQueenSide()) castles |= ImmutablePosition.BLACK_LONG_CASTLE;
    if (rights.blackKingSide()) castles |= ImmutablePosition.BLACK_SHORT_CASTLE;
    return castles;
  }

  private static CastlingRights fromChesspressoCastles(int castles) {
    return new CastlingRights(
        (castles & ImmutablePosition.WHITE_SHORT_CASTLE) != 0,
        (castles & ImmutablePosition.WHITE_LONG_CASTLE) != 0,
        (castles & ImmutablePosition.BLACK_SHORT_CASTLE) != 0,
        (castles & ImmutablePosition.BLACK_LONG_CASTLE) != 0);
  }

  private static int toChesspressoColor(PieceColor color) {
    return color == PieceColor.WHITE ? Chess.WHITE : Chess.BLACK;
  }

  private static int toChesspressoPiece(PieceType pieceType) {
    return switch (pieceType) {
      case KING -> Chess.KING;
      case QUEEN -> Chess.QUEEN;
      case ROOK -> Chess.ROOK;
      case BISHOP -> Chess.BISHOP;
      case KNIGHT -> Chess.KNIGHT;
      case PAWN -> Chess.PAWN;
    };
  }

  private static PieceType toPieceType(int piece) {
    return switch (piece) {
      case Chess.KING -> PieceType.KING;
      case Chess.QUEEN -> PieceType.QUEEN;
      case Chess.ROOK -> PieceType.ROOK;
      case Chess.BISHOP -> PieceType.BISHOP;
      case Chess.KNIGHT -> PieceType.KNIGHT;
      case Chess.PAWN -> PieceType.PAWN;
      default -> throw new IllegalArgumentException("Unsupported Chesspresso piece: " + piece);
    };
  }
}
