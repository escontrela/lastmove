package com.escontrela.lastmove.ui.screen;

/** Screens available in the primary LastMove window. */
public enum UiScreenId {
    MAIN("/fxml/main-window.fxml"),
    PGN_ANALYSIS("/fxml/pgn-analysis.fxml"),
    SETUP("/fxml/setup.fxml");

    private final String fxmlPath;

    UiScreenId(String fxmlPath) {
        this.fxmlPath = fxmlPath;
    }

    public String fxmlPath() {
        return fxmlPath;
    }
}
