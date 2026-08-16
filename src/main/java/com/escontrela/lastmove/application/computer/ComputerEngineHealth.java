package com.escontrela.lastmove.application.computer;

import com.escontrela.lastmove.domain.game.MoveCommand;
import java.util.Objects;
import java.util.Optional;

/** Result of checking an external engine installation and asking it for a legal probe move. */
public record ComputerEngineHealth(
    ComputerEngineDescriptor descriptor,
    boolean available,
    String message,
    Optional<MoveCommand> probeMove) {

  public ComputerEngineHealth {
    descriptor = Objects.requireNonNull(descriptor, "descriptor must not be null");
    message = Objects.requireNonNull(message, "message must not be null").trim();
    probeMove = Objects.requireNonNull(probeMove, "probeMove must not be null");
    if (message.isEmpty()) {
      throw new IllegalArgumentException("message must not be blank");
    }
    if (!available && probeMove.isPresent()) {
      throw new IllegalArgumentException("an unavailable engine cannot expose a probe move");
    }
    if (available && probeMove.isEmpty()) {
      throw new IllegalArgumentException("an available engine must expose its legal probe move");
    }
  }

  public static ComputerEngineHealth available(
      ComputerEngineDescriptor descriptor, String message, MoveCommand probeMove) {
    return new ComputerEngineHealth(descriptor, true, message, Optional.of(probeMove));
  }

  public static ComputerEngineHealth unavailable(
      ComputerEngineDescriptor descriptor, String message) {
    return new ComputerEngineHealth(descriptor, false, message, Optional.empty());
  }
}
