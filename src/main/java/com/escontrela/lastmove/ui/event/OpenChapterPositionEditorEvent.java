package com.escontrela.lastmove.ui.event;

import com.escontrela.lastmove.application.study.StudyChapterPositionEditContext;
import java.util.Objects;

/** Opens Position Editor for the initial position of one persisted study chapter. */
public record OpenChapterPositionEditorEvent(StudyChapterPositionEditContext context) {

  public OpenChapterPositionEditorEvent {
    Objects.requireNonNull(context, "context must not be null");
  }
}
