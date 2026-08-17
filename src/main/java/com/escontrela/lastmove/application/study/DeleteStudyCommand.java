package com.escontrela.lastmove.application.study;

import com.escontrela.lastmove.domain.player.PlayerId;
import com.escontrela.lastmove.domain.study.StudyId;
import java.util.Objects;

/** Deletes one owned study and all of its chapters. */
public record DeleteStudyCommand(PlayerId ownerId, StudyId studyId) {

  public DeleteStudyCommand {
    Objects.requireNonNull(ownerId, "ownerId must not be null");
    Objects.requireNonNull(studyId, "studyId must not be null");
  }
}