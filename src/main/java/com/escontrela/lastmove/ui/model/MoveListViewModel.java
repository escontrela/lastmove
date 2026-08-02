package com.escontrela.lastmove.ui.model;

import com.escontrela.lastmove.domain.game.Move;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.springframework.stereotype.Component;

/**
 * Presentation model for the move-list panel.
 *
 * <p>Holds the ordered list of moves and the index of the currently highlighted move.
 */
@Component
public class MoveListViewModel {

    private final ObservableList<Move> moves = FXCollections.observableArrayList();
    private final IntegerProperty currentIndex = new SimpleIntegerProperty(-1);

    public ObservableList<Move> getMoves() {
        return moves;
    }

    public IntegerProperty currentIndexProperty() {
        return currentIndex;
    }

    public int getCurrentIndex() {
        return currentIndex.get();
    }

    public void setCurrentIndex(int index) {
        currentIndex.set(index);
    }

    public void setMoves(java.util.List<Move> newMoves) {
        moves.setAll(newMoves);
    }
}
