package com.escontrela.lastmove.application.analysis;

import com.escontrela.lastmove.domain.analysis.AnalysisDocument;
import java.util.Objects;

/** Selected analysis-session variation prepared for a temporary tactic attempt. */
public record AnalysisSessionTacticSource(String title, AnalysisDocument document) {

  public AnalysisSessionTacticSource {
    title = Objects.requireNonNull(title, "title must not be null");
    Objects.requireNonNull(document, "document must not be null");
  }
}
