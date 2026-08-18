package com.escontrela.lastmove.domain.tactics;

import com.escontrela.lastmove.domain.player.PlayerId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Aggregate root for a player's ordered collection of tactical exercises. */
public final class TacticSuite {

  private final TacticSuiteId id;
  private final PlayerId ownerId;
  private String title;
  private Optional<String> description;
  private final List<TacticExercise> exercises = new ArrayList<>();
  private final Instant createdAt;
  private Instant updatedAt;

  private TacticSuite(
      TacticSuiteId id,
      PlayerId ownerId,
      String title,
      Optional<String> description,
      List<TacticExercise> exercises,
      Instant createdAt,
      Instant updatedAt) {
    this.id = Objects.requireNonNull(id, "id must not be null");
    this.ownerId = Objects.requireNonNull(ownerId, "ownerId must not be null");
    this.title = requireTitle(title);
    this.description = Objects.requireNonNull(description, "description must not be null");
    this.exercises.addAll(Objects.requireNonNull(exercises, "exercises must not be null"));
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
  }

  public static TacticSuite create(PlayerId ownerId, String title) {
    Instant now = Instant.now();
    return new TacticSuite(TacticSuiteId.random(), ownerId, title, Optional.empty(), List.of(), now, now);
  }

  public static TacticSuite restore(
      TacticSuiteId id,
      PlayerId ownerId,
      String title,
      Optional<String> description,
      List<TacticExercise> exercises,
      Instant createdAt,
      Instant updatedAt) {
    return new TacticSuite(id, ownerId, title, description, exercises, createdAt, updatedAt);
  }

  public void rename(String newTitle) {
    title = requireTitle(newTitle);
    touch();
  }

  public void setDescription(Optional<String> newDescription) {
    description = Objects.requireNonNull(newDescription, "description must not be null");
    touch();
  }

  public TacticExercise addExercise(TacticExercise exercise) {
    exercises.add(Objects.requireNonNull(exercise, "exercise must not be null"));
    touch();
    return exercise;
  }

  public boolean removeExercise(TacticExerciseId exerciseId) {
    boolean removed = exercises.removeIf(exercise -> exercise.id().equals(Objects.requireNonNull(exerciseId, "exerciseId must not be null")));
    if (removed) {
      touch();
    }
    return removed;
  }

  public boolean moveExercise(TacticExerciseId exerciseId, int offset) {
    Objects.requireNonNull(exerciseId, "exerciseId must not be null");
    int currentIndex = indexOf(exerciseId);
    int targetIndex = currentIndex + offset;
    if (currentIndex < 0 || targetIndex < 0 || targetIndex >= exercises.size()) {
      return false;
    }
    TacticExercise exercise = exercises.remove(currentIndex);
    exercises.add(targetIndex, exercise);
    touch();
    return true;
  }

  public void touch() {
    updatedAt = Instant.now();
  }

  public Optional<TacticExercise> exercise(TacticExerciseId exerciseId) {
    return exercises.stream().filter(exercise -> exercise.id().equals(exerciseId)).findFirst();
  }

  public TacticSuiteId id() { return id; }
  public PlayerId ownerId() { return ownerId; }
  public String title() { return title; }
  public Optional<String> description() { return description; }
  public List<TacticExercise> exercises() { return List.copyOf(exercises); }
  public Instant createdAt() { return createdAt; }
  public Instant updatedAt() { return updatedAt; }

  private int indexOf(TacticExerciseId exerciseId) {
    for (int index = 0; index < exercises.size(); index++) {
      if (exercises.get(index).id().equals(exerciseId)) return index;
    }
    return -1;
  }

  private String requireTitle(String value) {
    String required = Objects.requireNonNull(value, "title must not be null").trim();
    if (required.isEmpty()) throw new IllegalArgumentException("title must not be blank");
    return required;
  }
}
