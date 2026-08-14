package com.escontrela.lastmove.domain.game;

import com.escontrela.lastmove.domain.common.Square;
import java.util.List;
import java.util.Optional;

/**
 * Immutable outcome of validating one requested move against a position.
 *
 * <p>An accepted result describes the resulting position and all observable effects. Applying it
 * to a {@link ChessGame} or analysis session is a separate aggregate operation.
 */
public record MoveExecutionResult(
    boolean accepted,
    Optional<String> rejectionReason,
    PositionSnapshot newSnapshot,
    Optional<MoveDescriptor> move,
    Optional<PositionPiece> capturedPiece,
    boolean check,
    boolean mate,
    boolean stalemate,
    List<Square> legalDestinationsNextTurn) {

  public MoveExecutionResult {
    rejectionReason = Optional.ofNullable(rejectionReason).orElseThrow();
    newSnapshot = java.util.Objects.requireNonNull(newSnapshot, "newSnapshot must not be null");
    move = java.util.Objects.requireNonNull(move, "move must not be null");
    capturedPiece = java.util.Objects.requireNonNull(capturedPiece, "capturedPiece must not be null");
    legalDestinationsNextTurn =
        List.copyOf(
            java.util.Objects.requireNonNull(
                legalDestinationsNextTurn, "legalDestinationsNextTurn must not be null"));
  }

  public static MoveExecutionResult accepted(PositionSnapshot newSnapshot, MoveDescriptor move) {
    return new MoveExecutionResult(
        true,
        Optional.empty(),
        newSnapshot,
        Optional.of(move),
        Optional.empty(),
        newSnapshot.check(),
        newSnapshot.mate(),
        newSnapshot.stalemate(),
        List.of());
  }

  public static MoveExecutionResult rejected(PositionSnapshot currentSnapshot, String reason) {
    return new MoveExecutionResult(
        false,
        Optional.of(reason),
        currentSnapshot,
        Optional.empty(),
        Optional.empty(),
        currentSnapshot.check(),
        currentSnapshot.mate(),
        currentSnapshot.stalemate(),
        List.of());
  }
}
