package com.escontrela.lastmove.application.computer;

/** Observable lifecycle phase of a human-versus-computer game. */
public enum ComputerGamePhase {
  STARTING,
  WAITING_FOR_HUMAN,
  ENGINE_THINKING,
  FINISHED,
  ENGINE_ERROR
}
