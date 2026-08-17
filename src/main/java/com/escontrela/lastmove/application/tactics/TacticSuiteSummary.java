package com.escontrela.lastmove.application.tactics;

import com.escontrela.lastmove.domain.tactics.TacticSuiteId;
import java.time.Instant;
import java.util.Optional;

/** Lightweight list representation of a tactic suite. */
public record TacticSuiteSummary(
    TacticSuiteId suiteId,
    String title,
    Optional<String> description,
    int exerciseCount,
    Instant updatedAt) {}
