package com.escontrela.lastmove.ui.event;

import com.escontrela.lastmove.application.analysis.AnalysisPositionEditContext;
import java.util.Objects;

/** Opens Position Editor for the starting position of one analysis session. */
public record OpenAnalysisPositionEditorEvent(AnalysisPositionEditContext context) {

  public OpenAnalysisPositionEditorEvent {
    Objects.requireNonNull(context, "context must not be null");
  }
}