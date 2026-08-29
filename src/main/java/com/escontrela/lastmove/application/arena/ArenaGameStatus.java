package com.escontrela.lastmove.application.arena;

/** Remote game state persisted independently from the local chess aggregate. */
public enum ArenaGameStatus { STARTED, ACTIVE, FINISHED, STREAM_CLOSED, ERROR }
