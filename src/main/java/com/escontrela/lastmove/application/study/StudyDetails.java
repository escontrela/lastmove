package com.escontrela.lastmove.application.study;

import java.util.List;
import java.util.Objects;

/** Complete read model of one study: its summary and ordered chapters. */
public record StudyDetails(StudySummary study, List<StudyChapterSummary> chapters) {

  public StudyDetails {
    Objects.requireNonNull(study, "study must not be null");
    chapters = List.copyOf(Objects.requireNonNull(chapters, "chapters must not be null"));
  }
}