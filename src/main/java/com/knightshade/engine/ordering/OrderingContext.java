package com.knightshade.engine.ordering;

import com.knightshade.engine.board.Move;

/**
 * Everything the move orderer needs to rank the moves of a node, apart from the board itself.
 */
public record OrderingContext(
    int ply, KillerMoves killers, HistoryTable history, Move transpositionMove) {}
