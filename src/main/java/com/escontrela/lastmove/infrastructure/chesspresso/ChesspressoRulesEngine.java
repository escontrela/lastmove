package com.escontrela.lastmove.infrastructure.chesspresso;

import chesspresso.Chess;
import chesspresso.move.IllegalMoveException;
import chesspresso.move.Move;
import chesspresso.position.Position;
import com.escontrela.lastmove.domain.common.PieceType;
import com.escontrela.lastmove.domain.common.Square;
import com.escontrela.lastmove.domain.game.ChessRulesEngine;
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
 * Stateless Chesspresso implementation of the domain {@link ChessRulesEngine} contract.
 *
 * <p>Each operation reconstructs a short-lived Chesspresso position, executes rules there and maps
 * the result back to engine-neutral domain objects. The adapter never owns a game or analysis
 * session and no Chesspresso type crosses the infrastructure boundary.
 */
@Component
public final class ChesspressoRulesEngine implements ChessRulesEngine {

  @Override
  public PositionSnapshot startingPosition() {
    return positionFrom(Fen.startingPosition());
  }

  @Override
  public PositionSnapshot positionFrom(Fen fen) {
    Objects.requireNonNull(fen, "fen must not be null");
    return ChesspressoPositionSnapshotMapper.fromPosition(
        ChesspressoFenMapper.toPosition(fen), Optional.empty());
  }

  @Override
  public MoveExecutionResult execute(PositionSnapshot currentPosition, MoveCommand command) {
    Objects.requireNonNull(currentPosition, "currentPosition must not be null");
    Objects.requireNonNull(command, "command must not be null");
    Position position = ChesspressoPositionSnapshotMapper.toPosition(currentPosition);
    try {
      short legalMove = findLegalMove(position, command);
      return executeLegalMove(position, legalMove);
    } catch (IllegalArgumentException | IllegalMoveException exception) {
      return MoveExecutionResult.rejected(currentPosition, exception.getMessage());
    }
  }

  @Override
  public MoveExecutionResult execute(PositionSnapshot currentPosition, SanMove move) {
    Objects.requireNonNull(currentPosition, "currentPosition must not be null");
    SanMove requiredMove = Objects.requireNonNull(move, "move must not be null");
    Position position = ChesspressoPositionSnapshotMapper.toPosition(currentPosition);
    try {
      short legalMove = findLegalMove(position, requiredMove);
      return executeLegalMove(position, legalMove);
    } catch (IllegalArgumentException | IllegalMoveException exception) {
      return MoveExecutionResult.rejected(currentPosition, exception.getMessage());
    }
  }

  private MoveExecutionResult executeLegalMove(Position position, short legalMove)
      throws IllegalMoveException {
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
  }

  private short findLegalMove(Position position, MoveCommand command) {
    int from = Chess.coorToSqi(command.from().getFile(), command.from().getRank());
    int to = Chess.coorToSqi(command.to().getFile(), command.to().getRank());
    int requestedPromotion =
        command.promotion().map(piece -> (int) toChesspressoPiece(piece)).orElse((int) Chess.NO_PIECE);
    boolean promotionAvailable = false;
    for (short candidate : position.getAllMoves()) {
      if (Move.getFromSqi(candidate) != from || Move.getToSqi(candidate) != to) {
        continue;
      }
      if (Move.isPromotion(candidate)) {
        promotionAvailable = true;
      }
      if (promotionMatches(candidate, requestedPromotion)) {
        return candidate;
      }
    }
    if (promotionAvailable && requestedPromotion == Chess.NO_PIECE) {
      throw new IllegalArgumentException(
          "Promotion piece is required: queen, rook, bishop, or knight");
    }
    throw new IllegalArgumentException(
        "Illegal move: " + command.from().toAlgebraic() + "-" + command.to().toAlgebraic());
  }

  private short findLegalMove(Position position, SanMove requestedMove) {
    String requestedSan = normalizeSan(requestedMove.getValue());
    for (short candidate : position.getAllMoves()) {
      Position candidatePosition = new Position(position);
      try {
        candidatePosition.doMove(candidate);
      } catch (IllegalMoveException exception) {
        throw new IllegalStateException("A generated legal move could not be executed", exception);
      }
      String candidateSan = normalizeSan(candidatePosition.getLastMove().getSAN());
      if (candidateSan.equals(requestedSan)) {
        return candidate;
      }
    }
    throw new IllegalArgumentException("Illegal SAN move: " + requestedMove.getValue());
  }

  private String normalizeSan(String san) {
    return san.trim().replace('0', 'O');
  }

  private Optional<PositionPiece> capturedPiece(Position position, short move) {
    int capturedSquare = Move.getToSqi(move);
    if (Move.isEPMove(move)) {
      capturedSquare =
          Chess.coorToSqi(
              Chess.sqiToCol(capturedSquare), Chess.sqiToRow(Move.getFromSqi(move)));
    }
    return Optional.ofNullable(
        ChesspressoPositionSnapshotMapper.pieceAt(position, capturedSquare));
  }

  private MoveDescriptor toMoveDescriptor(Position position, short move) {
    Move lastMove = position.getLastMove();
    return new MoveDescriptor(
        Square.of(
            Chess.sqiToCol(Move.getFromSqi(move)), Chess.sqiToRow(Move.getFromSqi(move))),
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
    if (!Move.isPromotion(move)) {
      return requestedPromotion == Chess.NO_PIECE;
    }
    return requestedPromotion != Chess.NO_PIECE
        && Move.getPromotionPiece(move) == requestedPromotion;
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
