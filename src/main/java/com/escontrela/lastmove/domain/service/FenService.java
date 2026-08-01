package com.escontrela.lastmove.domain.service;

import com.escontrela.lastmove.domain.common.ChessConstants;
import com.escontrela.lastmove.domain.notation.Fen;

/**
 * Domain service for working with FEN strings.
 *
 * <p>Provides utility methods for validating, parsing, and converting FEN positions.
 * Does not depend on any third-party chess library.
 */
public class FenService {

    /**
     * Returns the standard starting-position FEN.
     */
    public Fen startingPosition() {
        return Fen.startingPosition();
    }

    /**
     * Returns {@code true} if the given string has the basic structural form of a FEN.
     * This is a lightweight check and does not validate chess legality.
     *
     * @param fen the FEN string to check
     */
    public boolean isWellFormed(String fen) {
        if (fen == null || fen.isBlank()) return false;
        String[] parts = fen.split("\\s+");
        return parts.length >= 4;
    }

    /**
     * Extracts the active color field from a FEN string.
     *
     * @param fen a well-formed FEN string
     * @return {@code "w"} or {@code "b"}
     */
    public String activeColor(Fen fen) {
        String[] parts = fen.getValue().split("\\s+");
        if (parts.length < 2) throw new IllegalArgumentException("Invalid FEN: " + fen);
        return parts[1];
    }
}
