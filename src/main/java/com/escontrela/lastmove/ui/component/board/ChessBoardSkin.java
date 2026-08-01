package com.escontrela.lastmove.ui.component.board;

import com.escontrela.lastmove.domain.common.ChessConstants;
import javafx.scene.control.SkinBase;
import javafx.scene.layout.GridPane;

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
        buildGrid(control);
        getChildren().add(grid);
    }

    private void buildGrid(ChessBoardControl control) {
        for (int rank = ChessConstants.RANKS - 1; rank >= 0; rank--) {
            for (int file = 0; file < ChessConstants.FILES; file++) {
                boolean isLight = (file + rank) % 2 != 0;
                ChessSquareControl square = new ChessSquareControl(file, rank, isLight, control.getTheme());
                grid.add(square, file, ChessConstants.RANKS - 1 - rank);
            }
        }
    }
}
