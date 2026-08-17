package com.escontrela.lastmove.ui.event;

import com.escontrela.lastmove.domain.study.StudyChapterId;
import com.escontrela.lastmove.domain.study.StudyId;
import java.util.Objects;

/** Requests the persisted-study workspace for one selected chapter. */
public record OpenStudyWorkspaceEvent(StudyId studyId, StudyChapterId chapterId) {

  public OpenStudyWorkspaceEvent {
    Objects.requireNonNull(studyId, "studyId must not be null");
    Objects.requireNonNull(chapterId, "chapterId must not be null");
  }
}
