package com.escontrela.lastmove.application.editor;

import com.escontrela.lastmove.domain.game.PositionSnapshot;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** The editor's transient state and the reasons that currently prevent FEN export. */
public record PositionEditorState(PositionSnapshot position, List<String> validationErrors) {
  public PositionEditorState {
    Objects.requireNonNull(position, "position must not be null");
    validationErrors = List.copyOf(Objects.requireNonNull(validationErrors, "validationErrors must not be null"));
  }

  public boolean valid() { return validationErrors.isEmpty(); }

  public Optional<String> validationMessage() {
    return valid() ? Optional.empty() : Optional.of(String.join(" ", validationErrors));
  }
}
