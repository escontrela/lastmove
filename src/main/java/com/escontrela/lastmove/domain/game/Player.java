package com.escontrela.lastmove.domain.game;

import com.escontrela.lastmove.domain.common.PieceColor;

import java.util.Objects;

/**
 * Represents one of the two players in a chess game.
 *
 * <p>A player has a name, an optional rating, and a side (color).
 */
public class Player {

    private final String name;
    private final PieceColor color;
    private final Integer elo;

    public Player(String name, PieceColor color, Integer elo) {
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.color = Objects.requireNonNull(color, "color must not be null");
        this.elo = elo;
    }

    public Player(String name, PieceColor color) {
        this(name, color, null);
    }

    public String getName() {
        return name;
    }

    public PieceColor getColor() {
        return color;
    }

    public java.util.Optional<Integer> getElo() {
        return java.util.Optional.ofNullable(elo);
    }

    @Override
    public String toString() {
        return elo != null ? name + " (" + elo + ")" : name;
    }
}
