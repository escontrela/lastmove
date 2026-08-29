package com.knightshade.engine.transposition;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.knightshade.engine.transposition.TranspositionTable.ScoreType;
import org.junit.jupiter.api.Test;

class TranspositionTableTest {

  @Test
  void doesNotReplaceADeepEntryWithAShallowQuiescenceResultForTheSamePosition() {
    TranspositionTable table = new TranspositionTable(16);
    long key = 42L;
    table.store(key, null, 6, 120, ScoreType.LOWER_BOUND);

    table.store(key, null, 0, 80, ScoreType.EXACT);

    assertEquals(6, table.probe(key).depth());
    assertEquals(120, table.probe(key).score());
    assertEquals(ScoreType.LOWER_BOUND, table.probe(key).type());
  }
}
