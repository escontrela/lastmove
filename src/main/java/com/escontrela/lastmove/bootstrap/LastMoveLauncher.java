package com.escontrela.lastmove.bootstrap;

/**
 * Main entry point for LastMove.
 *
 * <p>A separate launcher class is required so that the JavaFX runtime is not loaded too early
 * when the fat-jar manifest references a non-Application main class.
 */
public class LastMoveLauncher {

    public static void main(String[] args) {
        JavaFxApplication.launch(JavaFxApplication.class, args);
    }
}
