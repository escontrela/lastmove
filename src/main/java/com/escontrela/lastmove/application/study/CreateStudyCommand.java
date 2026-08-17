package com.escontrela.lastmove.application.study;

import com.escontrela.lastmove.domain.player.PlayerId;
import java.util.Objects;
import java.util.Optional;

/** Creates a new empty study owned by the supplied player. */
public record CreateStudyCommand(PlayerId ownerId, String title, Optional<String> description) {

  public CreateStudyCommand {
    Objects.requireNonNull(ownerId, "ownerId must not be null");
    title = Objects.requireNonNull(title, "title must not be null");
    description = Objects.requireNonNull(description, "description must not be null");
  }
}