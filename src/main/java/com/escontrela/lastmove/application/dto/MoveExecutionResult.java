package com.escontrela.lastmove.application.dto;

import com.escontrela.lastmove.domain.notation.Fen;
import java.util.Optional;

public record MoveExecutionResult(
    boolean accepted,
    Fen resultingPosition, // reutiliza el value object Fen que ya existe en domain.notation
    Optional<String> rejectionReason) {

  public static MoveExecutionResult accepted(Fen resultingPosition) {

    return new MoveExecutionResult(true, resultingPosition, Optional.empty());
  }

  public static MoveExecutionResult rejected(Fen currentPosition, String reason) {

    return new MoveExecutionResult(false, currentPosition, Optional.of(reason));
  }
}
