package com.escontrela.lastmove.application.arena;

/** Verifies a token against Lichess without exposing transport details to application callers. */
public interface LichessBotAccountVerifier {
  LichessBotAccount verifyBotToken(String token);
}
