package com.knightshade.engine.transposition;

import com.knightshade.engine.board.Move;
import java.util.Arrays;

/**
 * Four-way set-associative Zobrist table. A bounded bucket scan retains deep results when
 * unrelated shallow leaves collide, without probing chains or unbounded insertion loops.
 * Instances belong to one search thread.
 */
public final class TranspositionTable {
  public enum ScoreType { EXACT, LOWER_BOUND, UPPER_BOUND }

  public record Entry(Move move, int depth, int score, ScoreType type) {}

  private static final int DEFAULT_CAPACITY = 1 << 18;
  private static final int BUCKET_SIZE = 4;
  private final long[] keys;
  private final Entry[] entries;
  private final int bucketMask;

  public TranspositionTable() {
    this(DEFAULT_CAPACITY);
  }

  public TranspositionTable(int capacity) {
    if (capacity < 1 || capacity > (1 << 26)) {
      throw new IllegalArgumentException("capacity must be between 1 and 2^26");
    }
    int size = BUCKET_SIZE;
    while (size < capacity) {
      size <<= 1;
    }
    keys = new long[size];
    entries = new Entry[size];
    bucketMask = size / BUCKET_SIZE - 1;
  }

  public Entry probe(long key) {
    int start = bucket(key);
    for (int i = start; i < start + BUCKET_SIZE; i++) {
      if (entries[i] != null && keys[i] == key) {
        return entries[i];
      }
    }
    return null;
  }

  public void store(long key, Move move, int depth, int score, ScoreType type) {
    int start = bucket(key);
    int replacement = start;
    for (int i = start; i < start + BUCKET_SIZE; i++) {
      Entry current = entries[i];
      if (current != null && keys[i] == key) {
        if (current.depth() > depth
            || current.depth() == depth && current.type() == ScoreType.EXACT
                && type != ScoreType.EXACT) {
          return;
        }
        entries[i] = new Entry(move == null ? current.move() : move, depth, score, type);
        return;
      }
      if (current == null || entries[replacement] != null
          && quality(current) < quality(entries[replacement])) {
        replacement = i;
      }
    }
    Entry victim = entries[replacement];
    if (victim != null && quality(victim) > depth + 2) {
      return;
    }
    keys[replacement] = key;
    entries[replacement] = new Entry(move, depth, score, type);
  }

  private int quality(Entry entry) {
    return entry.depth() + (entry.type() == ScoreType.EXACT ? 2 : 0);
  }

  private int bucket(long key) {
    return ((int) key & bucketMask) * BUCKET_SIZE;
  }

  public void clear() {
    Arrays.fill(entries, null);
  }
}
