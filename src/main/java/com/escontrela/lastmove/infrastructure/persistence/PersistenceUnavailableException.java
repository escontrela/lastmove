package com.escontrela.lastmove.infrastructure.persistence;

/** Thrown when the local database is unavailable but the application continues running. */
public class PersistenceUnavailableException extends RuntimeException {

    public PersistenceUnavailableException(String message) {
        super(message);
    }
}
