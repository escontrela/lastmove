package com.escontrela.lastmove.ui.screen;

import javafx.scene.Scene;
import javafx.stage.Stage;

/** A view attached to the application's primary JavaFX stage. */
public interface UiScreen {

    UiScreenId id();

    Stage stage();

    Scene scene();

    UiScreenController controller();

    void show();

    void hide();

    boolean isShowing();
}
