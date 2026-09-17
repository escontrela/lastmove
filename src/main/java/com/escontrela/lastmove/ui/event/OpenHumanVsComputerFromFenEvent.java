package com.escontrela.lastmove.ui.event;

import com.escontrela.lastmove.domain.notation.Fen;
import java.util.Objects;

/** Opens the Human versus Computer setup with an application-provided starting position. */
public record OpenHumanVsComputerFromFenEvent(Fen startingPosition) {

  public OpenHumanVsComputerFromFenEvent {
    Objects.requireNonNull(startingPosition, "startingPosition must not be null");
  }
}
