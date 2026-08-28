package com.knightshade.engine.transposition;

import com.knightshade.engine.board.Move;
import java.util.Arrays;

/**
 * Direct-mapped transposition table keyed by Zobrist hash.
 *
 * <p>Each key maps to a single slot ({@code key & mask}). Collisions simply overwrite the slot
 * (always-replace), so lookups and stores are constant-time and can never loop, unlike linear
 * probing which deadlocks when the table reaches full occupancy.
 */
public final class TranspositionTable {

  public enum ScoreType {
    EXACT,
    LOWER_BOUND,
    UPPER_BOUND
  }

  public record Entry(Move move, int depth, int score, ScoreType type) {}

  private static final int DEFAULT_CAPACITY = 1 << 16;

  private final long[] keys;
  private final Entry[] entries;
  private final int mask;

  public TranspositionTable() {
    this(DEFAULT_CAPACITY);
  }

  public TranspositionTable(int capacity) {
    int size = 1;
    while (size < capacity) {
      size <<= 1;
    }
    keys = new long[size];
    entries = new Entry[size];
    mask = size - 1;
  }

  public Entry probe(long key) {
    int index = (int) (key & mask);
    Entry entry = entries[index];
    return entry != null && keys[index] == key ? entry : null;
  }

  public void store(long key, Move move, int depth, int score, ScoreType type) {
    int index = (int) (key & mask);
    Entry current = entries[index];
    if (current != null && keys[index] == key && current.depth() > depth) {
      return;
    }
    keys[index] = key;
    entries[index] = new Entry(move, depth, score, type);
  }

  public void clear() {
    Arrays.fill(entries, null);
  }
}
