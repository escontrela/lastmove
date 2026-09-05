package com.escontrela.lastmove.application.training.storm;

import com.escontrela.lastmove.domain.tactics.TacticExerciseReference;
import java.util.List;
import java.util.Set;

/** Application boundary for the global pool of persisted tactical exercises. */
@FunctionalInterface
public interface StormGameExerciseSource {
  List<TacticExerciseReference> findAllTrainableExercises();

  default Set<String> tagsFor(TacticExerciseReference reference) { return Set.of(); }
}
