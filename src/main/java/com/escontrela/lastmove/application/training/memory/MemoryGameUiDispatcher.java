package com.escontrela.lastmove.application.training.memory;

/** Dispatches observable state changes onto the UI thread. */
@FunctionalInterface
public interface MemoryGameUiDispatcher {
  void dispatch(Runnable mutation);

  static MemoryGameUiDispatcher immediate() {
    return Runnable::run;
  }
}
