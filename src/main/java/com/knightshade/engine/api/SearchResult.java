package com.knightshade.engine.api;

import com.knightshade.engine.board.Move;

/**
 * The outcome of one search: the chosen move plus a compact set of diagnostics.
 *
 * <p>{@code move} is {@code null} only when the input position has no legal move (checkmate or
 * stalemate).
 */
public record SearchResult(Move move, int score, int depth, long nodes, long elapsedMillis) {}
