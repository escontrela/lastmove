package com.escontrela.lastmove.application.training;

import java.time.Duration;

/** Neutral timing boundary shared by all timed training modes. */
public interface TrainingClock {
  void reset();
  Duration elapsed();
  TrainingCancellable schedule(Duration delay, Runnable callback);
  void cancelAll();
}
