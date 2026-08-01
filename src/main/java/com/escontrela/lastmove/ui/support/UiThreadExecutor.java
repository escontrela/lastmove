package com.escontrela.lastmove.ui.support;

import javafx.application.Platform;
import org.springframework.stereotype.Component;

/**
 * Utility that ensures Runnables are executed on the JavaFX application thread.
 *
 * <p>Used by event listeners in the application layer to push model updates to the UI safely.
 */
@Component
public class UiThreadExecutor {

    /**
     * Runs {@code action} on the JavaFX application thread.
     * If the current thread is already the FX thread the action is run immediately;
     * otherwise it is queued via {@link Platform#runLater(Runnable)}.
     *
     * @param action the action to run on the FX thread
     */
    public void run(Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            Platform.runLater(action);
        }
    }
}
