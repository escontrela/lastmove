package com.escontrela.lastmove.infrastructure.chesspresso;

import chesspresso.Chess;
import chesspresso.move.IllegalMoveException;
import chesspresso.move.Move;
import chesspresso.position.Position;
import com.escontrela.lastmove.domain.common.PieceColor;
import com.escontrela.lastmove.domain.common.PieceType;
import com.escontrela.lastmove.domain.common.Square;
import com.escontrela.lastmove.domain.game.ChessGameSession;
import com.escontrela.lastmove.domain.game.MoveCommand;
import com.escontrela.lastmove.domain.game.MoveDescriptor;
import com.escontrela.lastmove.domain.game.MoveExecutionResult;
import com.escontrela.lastmove.domain.game.PositionPiece;
import com.escontrela.lastmove.domain.game.PositionSnapshot;
import com.escontrela.lastmove.domain.notation.Fen;
import com.escontrela.lastmove.domain.notation.SanMove;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Chesspresso-backed mutable implementation for one chess session. */
public final class ChesspressoGameSession implements ChessGameSession {

  private final Position currentPosition;
  private Optional<MoveDescriptor> lastMove = Optional.empty();

  public ChesspressoGameSession(Fen initialPosition) {
    this.currentPosition = ChesspressoFenMapper.toPosition(initialPosition);
  }

  @Override
  public synchronized MoveExecutionResult tryMove(MoveCommand command) {
    PositionSnapshot beforeMove = extractSnapshot();
    try {
      short legalMove = mapLegalMove(command);
      currentPosition.doMove(legalMove);
      MoveDescriptor move = extractMoveDescriptor(legalMove);
      lastMove = Optional.of(move);
      PositionSnapshot snapshot = extractSnapshot();
      return MoveExecutionResult.accepted(snapshot, move);
    } catch (IllegalArgumentException | IllegalMoveException exception) {
      return MoveExecutionResult.rejected(beforeMove, exception.getMessage());
    }
  }

  @Override
  public synchronized PositionSnapshot currentSnapshot() {
    return extractSnapshot();
  }

  private short mapLegalMove(MoveCommand command) {
    int from = Chess.coorToSqi(command.from().getFile(), command.from().getRank());
    int to = Chess.coorToSqi(command.to().getFile(), command.to().getRank());
    int requestedPromotion =
        command.promotion().map(piece -> (int) toChesspressoPiece(piece)).orElse((int) Chess.NO_PIECE);

    for (short candidate : currentPosition.getAllMoves()) {
      if (Move.getFromSqi(candidate) == from
          && Move.getToSqi(candidate) == to
          && promotionMatches(candidate, requestedPromotion)) {
        return candidate;
      }
    }
    throw new IllegalArgumentException(
        "Illegal move: " + command.from().toAlgebraic() + "-" + command.to().toAlgebraic());
  }

  private PositionSnapshot extractSnapshot() {
    List<PositionPiece> pieces = new ArrayList<>();
    for (int squareIndex = 0; squareIndex < Chess.NUM_OF_SQUARES; squareIndex++) {
      int stone = currentPosition.getStone(squareIndex);
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
        currentPosition.getToPlay() == Chess.WHITE ? PieceColor.WHITE : PieceColor.BLACK,
        lastMove,
        currentPosition.isCheck(),
        currentPosition.isMate());
  }

  private MoveDescriptor extractMoveDescriptor(short move) {
    Move lastMove = currentPosition.getLastMove();
    return new MoveDescriptor(
        Square.of(Chess.sqiToCol(Move.getFromSqi(move)), Chess.sqiToRow(Move.getFromSqi(move))),
        Square.of(Chess.sqiToCol(Move.getToSqi(move)), Chess.sqiToRow(Move.getToSqi(move))),
        SanMove.of(lastMove.getSAN()),
        Move.isCapturing(move),
        Move.isCastle(move),
        Move.isEPMove(move),
        Move.isPromotion(move)
            ? Optional.of(toPieceType(Move.getPromotionPiece(move)))
            : Optional.empty());
  }

  private boolean promotionMatches(short move, int requestedPromotion) {
    if (!Move.isPromotion(move)) {
      return requestedPromotion == Chess.NO_PIECE;
    }
    return Move.getPromotionPiece(move)
        == (requestedPromotion == Chess.NO_PIECE ? Chess.QUEEN : requestedPromotion);
  }

  private int toChesspressoPiece(PieceType pieceType) {
    return switch (pieceType) {
      case QUEEN -> Chess.QUEEN;
      case ROOK -> Chess.ROOK;
      case BISHOP -> Chess.BISHOP;
      case KNIGHT -> Chess.KNIGHT;
      case KING, PAWN -> throw new IllegalArgumentException("A pawn cannot promote to " + pieceType);
    };
  }

  private PieceType toPieceType(int piece) {
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
