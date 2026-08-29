package com.escontrela.lastmove.application.arena;

/** Durable lifecycle of the local connection to Lichess account events. */
public enum ArenaConnectionStatus { DISCONNECTED, CONNECTING, CONNECTED, RECONNECTING, ERROR }
