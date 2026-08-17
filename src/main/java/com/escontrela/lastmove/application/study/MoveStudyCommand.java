package com.escontrela.lastmove.application.study;

import com.escontrela.lastmove.domain.player.PlayerId;
import com.escontrela.lastmove.domain.study.StudyId;
import java.util.Objects;

/** Moves one owned study by one place in the owner's library order. */
public record MoveStudyCommand(PlayerId ownerId, StudyId studyId, int offset) {

  public MoveStudyCommand {
    Objects.requireNonNull(ownerId, "ownerId must not be null");
    Objects.requireNonNull(studyId, "studyId must not be null");
  }
}