package com.escontrela.lastmove.application.event;

/** UI and notification-facing event emitted after a durable Arena state transition. */
public record LichessArenaEvent(Type type, String externalId, String detail) {
  public enum Type { CONNECTIVITY_CHANGED, CHALLENGE_RECEIVED, CHALLENGE_DECIDED, TOURNAMENT_PAIRING_RECEIVED, GAME_STARTED, GAME_UPDATED, GAME_FINISHED, TOURNAMENTS_UPDATED, TOURNAMENTS_FAILED, BOTS_UPDATED, BOT_CYCLE_UPDATED }
}
