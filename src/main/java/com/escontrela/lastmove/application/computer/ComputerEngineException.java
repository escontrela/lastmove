package com.escontrela.lastmove.application.computer;

/** Signals that a configured computer engine could not start, respond or return a usable move. */
public final class ComputerEngineException extends RuntimeException {

  public ComputerEngineException(String message) {
    super(message);
  }

  public ComputerEngineException(String message, Throwable cause) {
    super(message, cause);
  }
}
