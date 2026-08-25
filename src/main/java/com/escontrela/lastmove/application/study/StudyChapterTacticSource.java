package com.escontrela.lastmove.application.study;

import com.escontrela.lastmove.domain.analysis.AnalysisDocument;
import java.util.Objects;

/** Independent chapter data used to start an in-memory tactic attempt. */
public record StudyChapterTacticSource(String title, AnalysisDocument document) {

  public StudyChapterTacticSource {
    title = Objects.requireNonNull(title, "title must not be null");
    Objects.requireNonNull(document, "document must not be null");
  }
}
