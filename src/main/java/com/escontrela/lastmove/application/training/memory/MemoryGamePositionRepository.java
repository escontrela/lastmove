package com.escontrela.lastmove.application.training.memory;

import java.util.List;

/** Persistence boundary for played positions available to every memory-game user. */
public interface MemoryGamePositionRepository {
  List<MemoryGamePosition> findAllPlayedPositions();
}
