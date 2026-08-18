package com.escontrela.lastmove.domain.tactics;

import com.escontrela.lastmove.domain.player.PlayerId;
import java.util.List;
import java.util.Optional;

/** Persistence boundary for player-owned tactic suites and their solution trees. */
public interface TacticRepository {
  TacticSuite save(TacticSuite suite);
  Optional<TacticSuite> findByIdAndOwner(TacticSuiteId suiteId, PlayerId ownerId);
  List<TacticSuite> findAllByOwner(PlayerId ownerId);
  boolean deleteByIdAndOwner(TacticSuiteId suiteId, PlayerId ownerId);
  void deleteByOwner(PlayerId ownerId);
  boolean moveSuiteToIndex(PlayerId ownerId, TacticSuiteId suiteId, int targetIndex);
}
