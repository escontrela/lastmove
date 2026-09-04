package com.escontrela.lastmove.ui.service;

import com.escontrela.lastmove.application.training.memory.MemoryGameUiDispatcher;
import javafx.application.Platform;
import org.springframework.stereotype.Component;

/** Dispatches memory-game observer updates to the JavaFX application thread. */
@Component
public final class JavaFxMemoryGameUiDispatcher implements MemoryGameUiDispatcher {
  @Override public void dispatch(Runnable mutation) {
    if (Platform.isFxApplicationThread()) mutation.run();
    else Platform.runLater(mutation);
  }
}
