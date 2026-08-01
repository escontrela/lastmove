package com.escontrela.lastmove.domain.common;

/** Shared chess constants used across the domain model. */
public final class ChessConstants {

    /** The FEN string for the standard starting position. */
    public static final String STARTING_FEN =
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    /** Number of files on a chess board. */
    public static final int FILES = 8;

    /** Number of ranks on a chess board. */
    public static final int RANKS = 8;

    /** Total number of squares on a chess board. */
    public static final int SQUARES = FILES * RANKS;

    private ChessConstants() {}
}
