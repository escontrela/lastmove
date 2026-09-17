package com.escontrela.lastmove.ui.event;

import com.escontrela.lastmove.domain.notation.Fen;
import java.util.Objects;

/** Opens the computer-versus-computer setup with an application-provided starting position. */
public record OpenComputerVsComputerFromFenEvent(Fen startingPosition) {

  public OpenComputerVsComputerFromFenEvent {
    Objects.requireNonNull(startingPosition, "startingPosition must not be null");
  }
}
