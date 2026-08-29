package com.escontrela.lastmove.application.event;

/** UI and notification-facing event emitted after a durable Arena state transition. */
public record LichessArenaEvent(Type type, String externalId, String detail) {
  public enum Type { CONNECTIVITY_CHANGED, CHALLENGE_RECEIVED, CHALLENGE_DECIDED, GAME_STARTED, GAME_UPDATED, GAME_FINISHED }
}
