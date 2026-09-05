package com.escontrela.lastmove.infrastructure.persistence;

import com.escontrela.lastmove.application.training.storm.StormGameExerciseSource;
import com.escontrela.lastmove.domain.tactics.TacticExerciseReference;
import com.escontrela.lastmove.domain.tactics.TacticRepository;
import java.util.List;
import java.util.Set;
import java.util.Objects;
import com.escontrela.lastmove.application.service.TagService;
import com.escontrela.lastmove.application.tag.TagRepository;
import org.springframework.stereotype.Component;

/** Bridges the global tactic repository to the Storm application boundary. */
@Component
public final class RepositoryStormGameExerciseSource implements StormGameExerciseSource {
  private final TacticRepository repository;
  private final TagRepository tags;
  public RepositoryStormGameExerciseSource(TacticRepository repository, TagRepository tags) {
    this.repository = Objects.requireNonNull(repository, "repository must not be null");
    this.tags = Objects.requireNonNull(tags, "tags must not be null");
  }
  @Override public List<TacticExerciseReference> findAllTrainableExercises() { return repository.findAllTrainableExercises(); }
  @Override public Set<String> tagsFor(TacticExerciseReference reference) {
    return tags.findByTarget(TagService.tacticSuiteTarget(reference.suiteId())).stream()
        .map(tag -> tag.name()).collect(java.util.stream.Collectors.toUnmodifiableSet());
  }
}
