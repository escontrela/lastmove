package com.escontrela.lastmove.ui.screen;

import javafx.scene.Scene;
import javafx.stage.Stage;

/** Default primary-stage implementation of {@link UiScreen}. */
public final class StageUiScreen implements UiScreen {

    private final UiScreenId id;
    private final Stage stage;
    private final Scene scene;
    private final UiScreenController controller;

    public StageUiScreen(UiScreenId id, Stage stage, Scene scene, UiScreenController controller) {
        this.id = id;
        this.stage = stage;
        this.scene = scene;
        this.controller = controller;
    }

    @Override
    public UiScreenId id() {
        return id;
    }

    @Override
    public Stage stage() {
        return stage;
    }

    @Override
    public Scene scene() {
        return scene;
    }

    @Override
    public UiScreenController controller() {
        return controller;
    }

    @Override
    public void show() {
        controller.onShow();
        stage.show();
    }

    @Override
    public void hide() {
        controller.onHide();
        stage.hide();
    }
}
