package com.escontrela.lastmove.ui.screen;

/** Screens available in the primary LastMove window. */
public enum UiScreenId {
    MAIN("/fxml/main-window.fxml", 1280.0, 800.0),
    HUMAN_VS_COMPUTER("/fxml/progressive-game.fxml", 1280.0, 800.0),
    COMPUTER_VS_COMPUTER("/fxml/computer-vs-computer-game.fxml", 1280.0, 800.0),
    PGN_ANALYSIS("/fxml/pgn-analysis.fxml", 1280.0, 800.0),
    POSITION_EDITOR("/fxml/position-editor.fxml", 1280.0, 800.0),
    ANALYSIS_SESSIONS("/fxml/analysis-sessions.fxml", 1280.0, 800.0),
    STUDIES("/fxml/studies.fxml", 1280.0, 800.0),
    TACTICS("/fxml/tactics.fxml", 1280.0, 800.0),
    TACTICS_WORKSPACE("/fxml/tactics-workspace.fxml", 1280.0, 800.0),
    STUDY_DESTINATION("/fxml/study-destination.fxml", 1100.0, 720.0),
    STUDY_WORKSPACE("/fxml/study-workspace.fxml", 1280.0, 800.0),
    SETUP("/fxml/setup.fxml", 1100.0, 720.0),
    PLAYERS("/fxml/players.fxml", 1100.0, 720.0),
    MY_GAMES("/fxml/my-games.fxml", 1280.0, 800.0),
    GAME_STATISTICS("/fxml/game-statistics.fxml", 1280.0, 800.0),
    KNIGHTSHADE_ARENA("/fxml/knightshade-arena.fxml", 1280.0, 800.0),
    MEMORY_GAME("/fxml/memory-game.fxml", 1280.0, 800.0);

    private final String fxmlPath;
    private final double width;
    private final double height;

    UiScreenId(String fxmlPath, double width, double height) {
        this.fxmlPath = fxmlPath;
        this.width = width;
        this.height = height;
    }

    public String fxmlPath() {
        return fxmlPath;
    }

    public double width() {
        return width;
    }

    public double height() {
        return height;
    }
}
