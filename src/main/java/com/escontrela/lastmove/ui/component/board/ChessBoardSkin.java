package com.escontrela.lastmove.ui.component.board;

import com.escontrela.lastmove.domain.common.ChessConstants;
import javafx.scene.control.SkinBase;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.RowConstraints;

/**
 * Default skin for {@link ChessBoardControl}.
 *
 * <p>Renders an 8×8 grid of {@link ChessSquareControl} instances.
 * Piece images and interaction handlers are added in future milestones.
 */
public class ChessBoardSkin extends SkinBase<ChessBoardControl> {

    private final GridPane grid = new GridPane();

    public ChessBoardSkin(ChessBoardControl control) {
        super(control);
        configureGrid();
        buildGrid(control);
        getChildren().add(grid);
    }

    private void configureGrid() {
        grid.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        for (int index = 0; index < ChessConstants.FILES; index++) {
            ColumnConstraints column = new ColumnConstraints();
            column.setPercentWidth(100.0 / ChessConstants.FILES);
            column.setFillWidth(true);
            grid.getColumnConstraints().add(column);
        }
        for (int index = 0; index < ChessConstants.RANKS; index++) {
            RowConstraints row = new RowConstraints();
            row.setPercentHeight(100.0 / ChessConstants.RANKS);
            row.setFillHeight(true);
            grid.getRowConstraints().add(row);
        }
    }

    private void buildGrid(ChessBoardControl control) {
        for (int rank = ChessConstants.RANKS - 1; rank >= 0; rank--) {
            for (int file = 0; file < ChessConstants.FILES; file++) {
                boolean isLight = (file + rank) % 2 != 0;
                ChessSquareControl square = new ChessSquareControl(file, rank, isLight, control.getTheme());
                addSamplePiece(square, file, rank);
                grid.add(square, file, ChessConstants.RANKS - 1 - rank);
            }
        }
    }

    @Override
    protected void layoutChildren(double contentX, double contentY, double contentWidth, double contentHeight) {
        double side = Math.min(contentWidth, contentHeight);
        double x = contentX + (contentWidth - side) / 2.0;
        double y = contentY + (contentHeight - side) / 2.0;
        grid.resizeRelocate(x, y, side, side);
    }

    private void addSamplePiece(ChessSquareControl square, int file, int rank) {
        String color = rank < 2 ? "white" : rank > 5 ? "black" : null;
        if (color == null) {
            return;
        }
        String piece = rank == 1 || rank == 6 ? "pawn" : startingBackRankPiece(file);
        square.setPieceImage("/chess-pieces/" + color + "-" + piece + ".png");
    }

    private String startingBackRankPiece(int file) {
        return switch (file) {
            case 0, 7 -> "rook";
            case 1, 6 -> "knight";
            case 2, 5 -> "bishop";
            case 3 -> "queen";
            case 4 -> "king";
            default -> throw new IllegalArgumentException("Invalid chess file: " + file);
        };
    }
}
