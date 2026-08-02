package com.escontrela.lastmove.ui.component.board;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
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
    private final ImageView pieceImage = new ImageView();

    public ChessSquareControl(int file, int rank, boolean isLight, BoardTheme theme) {
        this.file = file;
        this.rank = rank;
        this.isLight = isLight;
        getStyleClass().add("chess-square");
        getStyleClass().add(isLight ? "chess-square-light" : "chess-square-dark");
        applyTheme(theme);
        setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        pieceImage.setPreserveRatio(true);
        pieceImage.fitWidthProperty().bind(widthProperty().multiply(0.82));
        pieceImage.fitHeightProperty().bind(heightProperty().multiply(0.82));
        getChildren().add(pieceImage);
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

    /** Sets a presentation-only piece image; this control does not model chess rules. */
    public void setPieceImage(String resourcePath) {
        Image image = new Image(ChessSquareControl.class.getResourceAsStream(resourcePath));
        pieceImage.setImage(image);
    }
}
