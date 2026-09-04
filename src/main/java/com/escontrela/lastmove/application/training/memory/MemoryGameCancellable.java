package com.escontrela.lastmove.application.training.memory;

/** Handle for cancelling one scheduled memory-game callback. */
@FunctionalInterface
public interface MemoryGameCancellable {
  void cancel();
}
