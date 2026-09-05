package com.escontrela.lastmove.application.training.memory;

import com.escontrela.lastmove.application.training.TrainingClock;
import com.escontrela.lastmove.application.training.TrainingCancellable;
import java.time.Duration;

/** Clock/scheduler boundary kept separate from memory-game rules for deterministic tests. */
public interface MemoryGameClock extends TrainingClock {
  /** Resets the session-relative clock to zero and clears implementation-owned timing state. */
  void reset();

  /** Returns elapsed time since the last reset. */
  Duration elapsed();

  /** Schedules a callback after the supplied delay relative to the current elapsed time. */
  @Override MemoryGameCancellable schedule(Duration delay, Runnable callback);

  /** Cancels all callbacks owned by this clock. */
  void cancelAll();
}
