package com.escontrela.lastmove.domain.common;

/** The color of a chess piece or side. */
public enum PieceColor {
    WHITE,
    BLACK;

    /** Returns the opponent color. */
    public PieceColor opposite() {
        return this == WHITE ? BLACK : WHITE;
    }
}
