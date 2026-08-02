package com.escontrela.lastmove.domain.service;

import com.escontrela.lastmove.domain.notation.PgnGame;

/**
 * Domain service for PGN-related operations that do not require third-party libraries.
 *
 * <p>Heavy PGN parsing is delegated to the infrastructure layer (Chesspresso).
 * This service handles lightweight domain rules, e.g. building PGN move text from a
 * {@link com.escontrela.lastmove.domain.game.MoveTree}.
 */
public class PgnService {

    /**
     * Returns a display-friendly title for a {@link PgnGame}, composed from its White, Black,
     * and Event headers.
     *
     * @param game the parsed PGN game
     * @return a non-null title string
     */
    public String title(PgnGame game) {
        String white = game.getWhite().orElse("?");
        String black = game.getBlack().orElse("?");
        String event = game.getEvent().orElse("?");
        return white + " vs. " + black + " – " + event;
    }
}
