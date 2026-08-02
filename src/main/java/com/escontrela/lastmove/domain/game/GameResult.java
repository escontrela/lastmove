package com.escontrela.lastmove.domain.game;

/**
 * The result of a chess game from White's perspective.
 */
public enum GameResult {
    WHITE_WINS("1-0"),
    BLACK_WINS("0-1"),
    DRAW("1/2-1/2"),
    UNKNOWN("*");

    private final String pgn;

    GameResult(String pgn) {
        this.pgn = pgn;
    }

    /** Returns the PGN result token for this result. */
    public String getPgn() {
        return pgn;
    }

    /** Parses a PGN result token into a {@link GameResult}. */
    public static GameResult fromPgn(String token) {
        for (GameResult r : values()) {
            if (r.pgn.equals(token)) return r;
        }
        return UNKNOWN;
    }
}
