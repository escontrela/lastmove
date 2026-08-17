package com.escontrela.lastmove.application.study;

import com.escontrela.lastmove.domain.player.PlayerId;
import com.escontrela.lastmove.domain.study.StudyChapterId;
import com.escontrela.lastmove.domain.study.StudyId;
import java.util.Objects;

/** Moves one chapter by one place within its study's chapter order. */
public record MoveChapterCommand(
    PlayerId ownerId, StudyId studyId, StudyChapterId chapterId, int offset) {

  public MoveChapterCommand {
    Objects.requireNonNull(ownerId, "ownerId must not be null");
    Objects.requireNonNull(studyId, "studyId must not be null");
    Objects.requireNonNull(chapterId, "chapterId must not be null");
  }
}