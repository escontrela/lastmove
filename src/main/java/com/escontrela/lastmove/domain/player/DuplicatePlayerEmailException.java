package com.escontrela.lastmove.domain.player;

/** Thrown when a player profile is created with an email that already exists. */
public class DuplicatePlayerEmailException extends RuntimeException {

    public DuplicatePlayerEmailException(String email) {
        super("A player with email '" + email + "' already exists");
    }
}
