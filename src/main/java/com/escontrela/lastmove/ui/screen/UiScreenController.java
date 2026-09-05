package com.escontrela.lastmove.ui.screen;

/** Lifecycle callbacks for a screen managed by {@link UiFlowManager}. */
public interface UiScreenController {

    /** Requests navigation away from this screen, allowing the screen to guard unsaved work. */
    default void requestExit(Runnable exitAction) {
        exitAction.run();
    }

    default void onShow() {
    }

    default void onHide() {
    }
}
