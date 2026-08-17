package com.escontrela.lastmove.application.study;

import com.escontrela.lastmove.domain.analysis.AnalysisSessionId;
import com.escontrela.lastmove.domain.player.PlayerId;
import com.escontrela.lastmove.domain.study.StudyId;
import java.util.Objects;

/** Archives an independent copy of an ephemeral analysis session as a chapter. */
public record CopySessionChapterCommand(
    PlayerId ownerId, StudyId studyId, String title, AnalysisSessionId sessionId) {

  public CopySessionChapterCommand {
    Objects.requireNonNull(ownerId, "ownerId must not be null");
    Objects.requireNonNull(studyId, "studyId must not be null");
    title = Objects.requireNonNull(title, "title must not be null");
    Objects.requireNonNull(sessionId, "sessionId must not be null");
  }
}