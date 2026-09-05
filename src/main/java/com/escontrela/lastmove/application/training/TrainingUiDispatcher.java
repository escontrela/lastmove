package com.escontrela.lastmove.application.training;

/** Dispatches observable training mutations onto the UI thread. */
@FunctionalInterface
public interface TrainingUiDispatcher {
  void dispatch(Runnable mutation);
  static TrainingUiDispatcher immediate() { return Runnable::run; }
}
