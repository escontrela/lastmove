package com.escontrela.lastmove.ui.model;

import com.escontrela.lastmove.domain.notation.Fen;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import org.springframework.stereotype.Component;

/**
 * Presentation model for the chess board area.
 *
 * <p>Holds the FEN string currently displayed and any highlight state.
 * Updated by the application layer; observed by {@link ChessBoardControl}.
 */
@Component
public class BoardViewModel {

    private final StringProperty fen = new SimpleStringProperty(Fen.startingPosition().getValue());

    public StringProperty fenProperty() {
        return fen;
    }

    public String getFen() {
        return fen.get();
    }

    public void setFen(String fen) {
        this.fen.set(fen);
    }
}
