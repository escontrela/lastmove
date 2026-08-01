package com.escontrela.lastmove.domain.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SquareTest {

    @Test
    void ofFileRank_createsCorrectSquare() {
        Square e4 = Square.of(4, 3);
        assertEquals(4, e4.getFile());
        assertEquals(3, e4.getRank());
        assertEquals("e4", e4.toAlgebraic());
    }

    @Test
    void ofAlgebraic_parsesCorrectly() {
        Square a1 = Square.of("a1");
        assertEquals(0, a1.getFile());
        assertEquals(0, a1.getRank());

        Square h8 = Square.of("h8");
        assertEquals(7, h8.getFile());
        assertEquals(7, h8.getRank());
    }

    @Test
    void equality_basedOnFileAndRank() {
        assertEquals(Square.of(4, 3), Square.of("e4"));
        assertNotEquals(Square.of(4, 3), Square.of("e5"));
    }

    @Test
    void invalidSquare_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> Square.of(8, 0));
        assertThrows(IllegalArgumentException.class, () -> Square.of(0, -1));
        assertThrows(IllegalArgumentException.class, () -> Square.of("z9"));
    }
}
