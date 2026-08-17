package com.escontrela.lastmove.domain.tactics;

import com.escontrela.lastmove.domain.analysis.AnalysisContent;
import com.escontrela.lastmove.domain.common.PieceColor;
import java.time.Instant;
import java.util.Objects;

/**
 * A position to solve and its authoritative tree of accepted solution moves.
 *
 * <p>The solution deliberately reuses {@link AnalysisContent}: it supports one or more correct
 * continuations without coupling a training attempt to the editor's navigation state. The side
 * that solves is always derived from the initial position's active color, never from board
 * orientation.
 */
public final class TacticExercise {

  private final TacticExerciseId id;
  private String title;
  private final AnalysisContent solution;
  private final Instant createdAt;
  private Instant updatedAt;

  public TacticExercise(
      TacticExerciseId id,
      String title,
      AnalysisContent solution,
      Instant createdAt,
      Instant updatedAt) {
    this.id = Objects.requireNonNull(id, "id must not be null");
    this.title = requireTitle(title);
    this.solution = Objects.requireNonNull(solution, "solution must not be null");
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
  }

  public void rename(String newTitle) {
    title = requireTitle(newTitle);
    touch();
  }

  /** Marks a solution-tree change as a modification of this exercise. */
  public void touch() {
    updatedAt = Instant.now();
  }

  public TacticExerciseId id() {
    return id;
  }

  public String title() {
    return title;
  }

  public AnalysisContent solution() {
    return solution;
  }

  /** Returns the color that must find the first solution move. */
  public PieceColor solverColor() {
    return solution.initialPosition().activeColor();
  }

  public boolean hasSolution() {
    return !solution.tree().roots().isEmpty();
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant updatedAt() {
    return updatedAt;
  }

  private String requireTitle(String value) {
    String required = Objects.requireNonNull(value, "title must not be null").trim();
    if (required.isEmpty()) {
      throw new IllegalArgumentException("title must not be blank");
    }
    return required;
  }
}
