package com.escontrela.lastmove.infrastructure.chesspresso;

import chesspresso.Chess;
import chesspresso.move.IllegalMoveException;
import chesspresso.move.Move;
import chesspresso.position.Position;
import com.escontrela.lastmove.domain.common.PieceType;
import com.escontrela.lastmove.domain.common.Square;
import com.escontrela.lastmove.domain.game.MoveCommand;
import com.escontrela.lastmove.domain.game.MoveDescriptor;
import com.escontrela.lastmove.domain.game.MoveExecutionResult;
import com.escontrela.lastmove.domain.game.PositionPiece;
import com.escontrela.lastmove.domain.game.PositionSnapshot;
import com.escontrela.lastmove.domain.notation.Fen;
import com.escontrela.lastmove.domain.notation.SanMove;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Stateless Chesspresso-backed validator for one requested move.
 *
 * <p>It reconstructs an engine position from the supplied snapshot, validates and applies the
 * move only to that short-lived engine object, then returns an engine-neutral result. It never
 * owns or mutates a {@code GameSession}.
 */
@Component
public final class ChesspressoMoveValidator {

  /** Returns a complete snapshot for the standard initial chess position. */
  public PositionSnapshot startingPosition() {
    return snapshotFor(Fen.startingPosition());
  }

  /** Converts a FEN position into a complete engine-neutral snapshot. */
  public PositionSnapshot snapshotFor(Fen fen) {
    Objects.requireNonNull(fen, "fen must not be null");
    return ChesspressoPositionSnapshotMapper.fromPosition(
        ChesspressoFenMapper.toPosition(fen), Optional.empty());
  }

  /**
   * Validates {@code command} against {@code currentPosition} without changing that snapshot.
   *
   * @return an accepted result with a fresh snapshot, or a rejected result retaining the input
   *     snapshot
   */
  public MoveExecutionResult validate(PositionSnapshot currentPosition, MoveCommand command) {
    Objects.requireNonNull(currentPosition, "currentPosition must not be null");
    Objects.requireNonNull(command, "command must not be null");
    Position position = ChesspressoPositionSnapshotMapper.toPosition(currentPosition);
    try {
      short legalMove = findLegalMove(position, command);
      Optional<PositionPiece> capturedPiece = capturedPiece(position, legalMove);
      position.doMove(legalMove);
      MoveDescriptor move = toMoveDescriptor(position, legalMove);
      PositionSnapshot snapshot =
          ChesspressoPositionSnapshotMapper.fromPosition(position, Optional.of(move));
      return new MoveExecutionResult(
          true,
          Optional.empty(),
          snapshot,
          Optional.of(move),
          capturedPiece,
          snapshot.check(),
          snapshot.mate(),
          snapshot.stalemate(),
          legalDestinations(position));
    } catch (IllegalArgumentException | IllegalMoveException exception) {
      return MoveExecutionResult.rejected(currentPosition, exception.getMessage());
    }
  }

  private short findLegalMove(Position position, MoveCommand command) {
    int from = Chess.coorToSqi(command.from().getFile(), command.from().getRank());
    int to = Chess.coorToSqi(command.to().getFile(), command.to().getRank());
    int requestedPromotion =
        command.promotion().map(piece -> (int) toChesspressoPiece(piece)).orElse((int) Chess.NO_PIECE);
    for (short candidate : position.getAllMoves()) {
      if (Move.getFromSqi(candidate) == from
          && Move.getToSqi(candidate) == to
          && promotionMatches(candidate, requestedPromotion)) {
        return candidate;
      }
    }
    throw new IllegalArgumentException(
        "Illegal move: " + command.from().toAlgebraic() + "-" + command.to().toAlgebraic());
  }

  private Optional<PositionPiece> capturedPiece(Position position, short move) {
    int capturedSquare = Move.getToSqi(move);
    if (Move.isEPMove(move)) {
      capturedSquare = Chess.coorToSqi(Chess.sqiToCol(capturedSquare), Chess.sqiToRow(Move.getFromSqi(move)));
    }
    return Optional.ofNullable(ChesspressoPositionSnapshotMapper.pieceAt(position, capturedSquare));
  }

  private MoveDescriptor toMoveDescriptor(Position position, short move) {
    Move lastMove = position.getLastMove();
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

  private List<Square> legalDestinations(Position position) {
    List<Square> destinations = new ArrayList<>();
    for (short move : position.getAllMoves()) {
      Square destination =
          Square.of(Chess.sqiToCol(Move.getToSqi(move)), Chess.sqiToRow(Move.getToSqi(move)));
      if (!destinations.contains(destination)) {
        destinations.add(destination);
      }
    }
    return List.copyOf(destinations);
  }

  private boolean promotionMatches(short move, int requestedPromotion) {
    if (!Move.isPromotion(move)) return requestedPromotion == Chess.NO_PIECE;
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
