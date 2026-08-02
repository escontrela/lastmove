package com.escontrela.lastmove.ui.component.board;

import javafx.scene.layout.StackPane;

/**
 * A single square on the {@link ChessBoardControl}.
 *
 * <p>Owns the background color, highlight state, and the piece image (if any).
 */
public class ChessSquareControl extends StackPane {

    private final int file;
    private final int rank;
    private final boolean isLight;

    public ChessSquareControl(int file, int rank, boolean isLight, BoardTheme theme) {
        this.file = file;
        this.rank = rank;
        this.isLight = isLight;
        getStyleClass().add("chess-square");
        getStyleClass().add(isLight ? "chess-square-light" : "chess-square-dark");
        applyTheme(theme);
        setPrefSize(64, 64);
    }

    private void applyTheme(BoardTheme theme) {
        String color = isLight ? theme.getLightColor() : theme.getDarkColor();
        setStyle("-fx-background-color: " + color + ";");
    }

    public int getFile() {
        return file;
    }

    public int getRank() {
        return rank;
    }
}
