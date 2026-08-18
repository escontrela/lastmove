package com.escontrela.lastmove.application.tactics;

import java.util.List;

/** Full suite metadata and its ordered exercises. */
public record TacticSuiteDetails(TacticSuiteSummary suite, List<TacticExerciseSummary> exercises) {
  public TacticSuiteDetails {
    exercises = List.copyOf(exercises);
  }
}
