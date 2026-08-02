package com.escontrela.lastmove.ui.component.board;

import com.escontrela.lastmove.domain.common.ChessConstants;
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

    private BoardTheme theme = BoardTheme.CLASSIC;

    public ChessBoardControl() {
        getStyleClass().add("chess-board");
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
        getSkin().dispose();
        setSkin(createDefaultSkin());
    }
}
