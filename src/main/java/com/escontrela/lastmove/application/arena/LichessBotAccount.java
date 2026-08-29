package com.escontrela.lastmove.application.arena;

import java.util.Objects;

/** Safe account information returned after validating a configured Lichess bot token. */
public record LichessBotAccount(String id, String username) {
  public LichessBotAccount {
    id = required(id, "id");
    username = required(username, "username");
  }

  private static String required(String value, String name) {
    String required = Objects.requireNonNull(value, name + " must not be null").trim();
    if (required.isEmpty()) throw new IllegalArgumentException(name + " must not be blank");
    return required;
  }
}
