package com.escontrela.lastmove.application.dto;

import java.util.Objects;
import java.util.Optional;

/**
 * UI-safe summary of one engine analysis over a position.
 *
 * <p>All fields are display-ready strings or plain values, so presentation code never depends on
 * engine or rules types. The best move is empty when the position has no legal move; the score is
 * empty when the engine reports no evaluation.
 */
public record PositionAnalysisResult(
    String engineDisplayName,
    Optional<String> bestMoveSan,
    Optional<String> scoreText,
    Optional<Integer> depth) {

  public PositionAnalysisResult {
    engineDisplayName = requireText(engineDisplayName, "engineDisplayName");
    bestMoveSan = Objects.requireNonNull(bestMoveSan, "bestMoveSan must not be null");
    scoreText = Objects.requireNonNull(scoreText, "scoreText must not be null");
    depth = Objects.requireNonNull(depth, "depth must not be null");
  }

  private static String requireText(String value, String field) {
    String required = Objects.requireNonNull(value, field + " must not be null").trim();
    if (required.isEmpty()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return required;
  }
}
