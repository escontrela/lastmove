package com.escontrela.lastmove.application.study;

import com.escontrela.lastmove.domain.player.PlayerId;
import com.escontrela.lastmove.domain.study.StudyChapterId;
import com.escontrela.lastmove.domain.study.StudyId;
import java.util.Objects;

/** Renames one chapter of an owned study. */
public record RenameChapterCommand(
    PlayerId ownerId, StudyId studyId, StudyChapterId chapterId, String title) {

  public RenameChapterCommand {
    Objects.requireNonNull(ownerId, "ownerId must not be null");
    Objects.requireNonNull(studyId, "studyId must not be null");
    Objects.requireNonNull(chapterId, "chapterId must not be null");
    title = Objects.requireNonNull(title, "title must not be null");
  }
}