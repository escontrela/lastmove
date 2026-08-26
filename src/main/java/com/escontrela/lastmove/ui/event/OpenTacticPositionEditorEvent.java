package com.escontrela.lastmove.ui.event;

import com.escontrela.lastmove.application.tactics.TacticPositionEditContext;
import java.util.Objects;

/** Opens Position Editor to compose the starting position of a new tactic exercise. */
public record OpenTacticPositionEditorEvent(TacticPositionEditContext context) {

  public OpenTacticPositionEditorEvent {
    Objects.requireNonNull(context, "context must not be null");
  }
}