package com.escontrela.lastmove.ui.screen;

/** Coordinates navigation between views hosted by the single primary window. */
public class UiFlowManager {

    private final UiScreenFactory screenFactory;
    private UiScreen currentScreen;

    public UiFlowManager(UiScreenFactory screenFactory) {
        this.screenFactory = screenFactory;
    }

    public void show(UiScreenId screenId) {
        UiScreen nextScreen = screenFactory.create(screenId);
        if (currentScreen != null) {
            currentScreen.controller().onHide();
        }
        nextScreen.show();
        currentScreen = nextScreen;
    }
}
