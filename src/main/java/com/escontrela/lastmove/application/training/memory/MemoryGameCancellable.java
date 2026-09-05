package com.escontrela.lastmove.application.training.memory;

import com.escontrela.lastmove.application.training.TrainingCancellable;

/** Handle for cancelling one scheduled memory-game callback. */
@FunctionalInterface
public interface MemoryGameCancellable extends TrainingCancellable {
  void cancel();
}
