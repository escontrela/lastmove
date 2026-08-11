package com.escontrela.lastmove.ui.screen;

/** Lifecycle callbacks for a screen managed by {@link UiFlowManager}. */
public interface UiScreenController {

    default void onShow() {
    }

    default void onHide() {
    }
}
