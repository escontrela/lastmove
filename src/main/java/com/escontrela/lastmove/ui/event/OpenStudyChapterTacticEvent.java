package com.escontrela.lastmove.ui.event;

import com.escontrela.lastmove.domain.study.StudyChapterId;
import com.escontrela.lastmove.domain.study.StudyId;
import java.util.Objects;

/** Requests running an existing study chapter as a one-off, non-persisted tactic. */
public record OpenStudyChapterTacticEvent(StudyId studyId, StudyChapterId chapterId) {

  public OpenStudyChapterTacticEvent {
    Objects.requireNonNull(studyId, "studyId must not be null");
    Objects.requireNonNull(chapterId, "chapterId must not be null");
  }
}
