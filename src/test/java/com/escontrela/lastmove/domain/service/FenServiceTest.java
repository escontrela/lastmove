package com.escontrela.lastmove.domain.service;

import com.escontrela.lastmove.domain.common.ChessConstants;
import com.escontrela.lastmove.domain.common.PieceColor;
import com.escontrela.lastmove.domain.common.PieceType;
import com.escontrela.lastmove.domain.common.Square;
import com.escontrela.lastmove.domain.game.CastlingRights;
import com.escontrela.lastmove.domain.game.PositionPiece;
import com.escontrela.lastmove.domain.game.PositionSnapshot;
import com.escontrela.lastmove.domain.notation.Fen;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FenServiceTest {

    private final FenService service = new FenService();

    @Test
    void startingPosition_returnsStandardFen() {
        Fen fen = service.startingPosition();
        assertEquals(ChessConstants.STARTING_FEN, fen.getValue());
    }

    @Test
    void isWellFormed_validFen_returnsTrue() {
        assertTrue(service.isWellFormed(ChessConstants.STARTING_FEN));
    }

    @Test
    void isWellFormed_null_returnsFalse() {
        assertFalse(service.isWellFormed(null));
    }

    @Test
    void isWellFormed_blank_returnsFalse() {
        assertFalse(service.isWellFormed("   "));
    }

    @Test
    void activeColor_startingPosition_returnsWhite() {
        assertEquals("w", service.activeColor(Fen.startingPosition()));
    }

    @Test
    void fromSnapshot_serializesBoardAndCompleteRulesState() {
        PositionSnapshot snapshot = new PositionSnapshot(
                List.of(
                        new PositionPiece(Square.of("a1"), PieceType.ROOK, PieceColor.WHITE),
                        new PositionPiece(Square.of("e1"), PieceType.KING, PieceColor.WHITE),
                        new PositionPiece(Square.of("h8"), PieceType.KING, PieceColor.BLACK)),
                PieceColor.BLACK,
                new CastlingRights(true, false, false, false),
                Optional.of(Square.of("e3")),
                7,
                42,
                Optional.empty(),
                false,
                false,
                false);

        assertEquals("7k/8/8/8/8/8/8/R3K3 b K e3 7 42",
                service.fromSnapshot(snapshot).getValue());
    }
}
