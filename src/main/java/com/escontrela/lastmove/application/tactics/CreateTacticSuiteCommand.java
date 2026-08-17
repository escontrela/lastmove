package com.escontrela.lastmove.application.tactics;

import com.escontrela.lastmove.domain.player.PlayerId;
import java.util.Objects;
import java.util.Optional;

/** Input for creating one player-owned suite. */
public record CreateTacticSuiteCommand(PlayerId ownerId, String title, Optional<String> description) {
  public CreateTacticSuiteCommand {
    Objects.requireNonNull(ownerId, "ownerId must not be null");
    Objects.requireNonNull(title, "title must not be null");
    Objects.requireNonNull(description, "description must not be null");
  }
}
