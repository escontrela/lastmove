package com.escontrela.lastmove.application.training.memory;

import com.escontrela.lastmove.application.training.TrainingUiDispatcher;

/** Dispatches observable state changes onto the UI thread. */
@FunctionalInterface
public interface MemoryGameUiDispatcher extends TrainingUiDispatcher {
  void dispatch(Runnable mutation);

  static MemoryGameUiDispatcher immediate() {
    return Runnable::run;
  }
}
