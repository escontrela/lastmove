package com.escontrela.lastmove.domain.common;

import java.util.Objects;

/**
 * An immutable board square identified by file (a–h) and rank (1–8).
 *
 * <p>Squares are value objects; equality is based on file and rank.
 */
public final class Square {

    private final int file; // 0 = a, 7 = h
    private final int rank; // 0 = rank 1, 7 = rank 8

    private Square(int file, int rank) {
        if (file < 0 || file > 7 || rank < 0 || rank > 7) {
            throw new IllegalArgumentException("Invalid square: file=" + file + " rank=" + rank);
        }
        this.file = file;
        this.rank = rank;
    }

    /**
     * Creates a square from zero-based file and rank indices.
     *
     * @param file 0 (a-file) to 7 (h-file)
     * @param rank 0 (rank 1) to 7 (rank 8)
     */
    public static Square of(int file, int rank) {
        return new Square(file, rank);
    }

    /**
     * Creates a square from algebraic notation such as {@code "e4"}.
     *
     * @param algebraic two-character string, e.g. "a1", "h8"
     */
    public static Square of(String algebraic) {
        Objects.requireNonNull(algebraic, "algebraic must not be null");
        if (algebraic.length() != 2) {
            throw new IllegalArgumentException("Invalid algebraic notation: " + algebraic);
        }
        int file = algebraic.charAt(0) - 'a';
        int rank = algebraic.charAt(1) - '1';
        return new Square(file, rank);
    }

    public int getFile() {
        return file;
    }

    public int getRank() {
        return rank;
    }

    /** Returns the algebraic name of this square, e.g. {@code "e4"}. */
    public String toAlgebraic() {
        return String.valueOf((char) ('a' + file)) + (rank + 1);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Square other)) return false;
        return file == other.file && rank == other.rank;
    }

    @Override
    public int hashCode() {
        return Objects.hash(file, rank);
    }

    @Override
    public String toString() {
        return toAlgebraic();
    }
}
