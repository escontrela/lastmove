package com.escontrela.lastmove.application.training.storm;

import com.escontrela.lastmove.domain.tactics.TacticExerciseReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.HashSet;
import java.util.Set;
import java.util.random.RandomGenerator;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

/** Selects a shuffled global exercise pool without immediate repeats across pool cycles. */
@Component
public final class StormGameExerciseSelector {
  private final StormGameExerciseSource source;
  private final RandomGenerator random;
  private List<TacticExerciseReference> remaining = List.of();
  private String lastExerciseId;
  private int presentedPuzzles;
  private StormDifficulty remainingDifficulty;
  private final Set<String> presentedExerciseIds = new HashSet<>();

  public StormGameExerciseSelector(StormGameExerciseSource source, RandomGenerator random) {
    this.source = Objects.requireNonNull(source, "source must not be null");
    this.random = Objects.requireNonNull(random, "random must not be null");
  }

  @Autowired
  public StormGameExerciseSelector(StormGameExerciseSource source) {
    this(source, RandomGenerator.getDefault());
  }

  /** Returns the next usable challenge, or empty when no persisted exercise is reproducible. */
  public Optional<StormGameChallenge> next() {
    StormDifficulty difficulty = StormDifficulty.forPresentedPuzzles(presentedPuzzles);
    if (remainingDifficulty != difficulty) remaining = List.of();
    discardPresentedExercisesFromPool();
    if (remaining.isEmpty()) refill();
    discardPresentedExercisesFromPool();
    if (remaining.isEmpty()) return Optional.empty();
    TacticExerciseReference reference = remaining.removeFirst();
    lastExerciseId = reference.exercise().id().value().toString();
    presentedExerciseIds.add(lastExerciseId);
    presentedPuzzles++;
    return Optional.of(toChallenge(reference, difficulty));
  }

  public void reset() {
    remaining = List.of();
    lastExerciseId = null;
    presentedPuzzles = 0;
    remainingDifficulty = null;
    presentedExerciseIds.clear();
  }

  private void refill() {
    List<TacticExerciseReference> eligible = source.findAllTrainableExercises().stream()
        .filter(Objects::nonNull)
        .filter(reference -> reference.exercise().hasSolution())
        .filter(reference -> !presentedExerciseIds.contains(reference.exercise().id().value().toString()))
        .toList();
    if (eligible.isEmpty()) {
      remaining = List.of();
      return;
    }
    StormDifficulty difficulty = StormDifficulty.forPresentedPuzzles(presentedPuzzles);
    List<TacticExerciseReference> preferred = eligible.stream()
        .filter(reference -> StormDifficulty.matchesTag(source.tagsFor(reference), difficulty))
        .toList();
    List<TacticExerciseReference> shuffled = new ArrayList<>(preferred.isEmpty() ? eligible : preferred);
    shuffle(shuffled);
    if (shuffled.size() > 1 && shuffled.getFirst().exercise().id().value().toString().equals(lastExerciseId)) {
      Collections.swap(shuffled, 0, 1);
    }
    remaining = shuffled;
    remainingDifficulty = difficulty;
  }

  private void discardPresentedExercisesFromPool() {
    remaining = new ArrayList<>(remaining.stream()
        .filter(reference -> !presentedExerciseIds.contains(reference.exercise().id().value().toString()))
        .toList());
  }

  private StormGameChallenge toChallenge(TacticExerciseReference reference, StormDifficulty difficulty) {
    return new StormGameChallenge(
        reference.ownerId(), reference.suiteId(), reference.exercise().id(),
        reference.exercise().title(), reference.exercise().solution().initialPosition(),
        reference.exercise().solverColor(), difficulty);
  }

  private void shuffle(List<TacticExerciseReference> values) {
    for (int index = values.size() - 1; index > 0; index--) {
      Collections.swap(values, index, random.nextInt(index + 1));
    }
  }
}
