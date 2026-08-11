package com.escontrela.lastmove.domain.game;

import java.util.Optional;

/** Result of validating and, when legal, applying a requested move. */
public record MoveExecutionResult(
    boolean accepted,
    Optional<String> rejectionReason,
    PositionSnapshot newSnapshot,
    Optional<MoveDescriptor> move,
    boolean check,
    boolean mate) {

  public static MoveExecutionResult accepted(PositionSnapshot newSnapshot, MoveDescriptor move) {
    return new MoveExecutionResult(
        true, Optional.empty(), newSnapshot, Optional.of(move), newSnapshot.check(), newSnapshot.mate());
  }

  public static MoveExecutionResult rejected(PositionSnapshot currentSnapshot, String reason) {
    return new MoveExecutionResult(
        false,
        Optional.of(reason),
        currentSnapshot,
        Optional.empty(),
        currentSnapshot.check(),
        currentSnapshot.mate());
  }
}
