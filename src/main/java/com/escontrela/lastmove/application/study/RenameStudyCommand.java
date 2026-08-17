package com.escontrela.lastmove.application.study;

import com.escontrela.lastmove.domain.player.PlayerId;
import com.escontrela.lastmove.domain.study.StudyId;
import java.util.Objects;

/** Renames one owned study. */
public record RenameStudyCommand(PlayerId ownerId, StudyId studyId, String title) {

  public RenameStudyCommand {
    Objects.requireNonNull(ownerId, "ownerId must not be null");
    Objects.requireNonNull(studyId, "studyId must not be null");
    title = Objects.requireNonNull(title, "title must not be null");
  }
}