package com.escontrela.lastmove.domain.service;

import com.escontrela.lastmove.domain.common.ChessConstants;
import com.escontrela.lastmove.domain.notation.Fen;
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
}
