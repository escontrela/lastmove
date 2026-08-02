package com.escontrela.lastmove.ui.component.board;

import javafx.scene.control.Control;
import javafx.scene.control.Skin;

/**
 * A reusable JavaFX control that renders a chess board.
 *
 * <p>The control receives a position (as a FEN string or a view model) and renders
 * it using {@link ChessBoardSkin}. It owns only rendering and user interaction –
 * it does not validate chess rules or parse PGN.
 */
public class ChessBoardControl extends Control {

    private BoardTheme theme = BoardTheme.LASTMOVE;

    public ChessBoardControl() {
        getStyleClass().add("chess-board");
        setMinSize(320, 320);
        setPrefSize(640, 640);
        setMaxSize(640, 640);
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new ChessBoardSkin(this);
    }

    public BoardTheme getTheme() {
        return theme;
    }

    public void setTheme(BoardTheme theme) {
        this.theme = theme;
        if (getSkin() != null) {
            getSkin().dispose();
            setSkin(createDefaultSkin());
        }
    }
}
