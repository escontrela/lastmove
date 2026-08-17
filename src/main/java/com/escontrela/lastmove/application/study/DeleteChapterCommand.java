package com.escontrela.lastmove.application.study;

import com.escontrela.lastmove.domain.player.PlayerId;
import com.escontrela.lastmove.domain.study.StudyChapterId;
import com.escontrela.lastmove.domain.study.StudyId;
import java.util.Objects;

/** Deletes one chapter from an owned study. */
public record DeleteChapterCommand(
    PlayerId ownerId, StudyId studyId, StudyChapterId chapterId) {

  public DeleteChapterCommand {
    Objects.requireNonNull(ownerId, "ownerId must not be null");
    Objects.requireNonNull(studyId, "studyId must not be null");
    Objects.requireNonNull(chapterId, "chapterId must not be null");
  }
}