package com.escontrela.lastmove.application.computer;

import java.util.concurrent.CompletionStage;

/** Asynchronous installation and legal-move probe for one configured computer opponent. */
public interface ComputerEngineHealthCheck {

  ComputerEngineDescriptor descriptor();

  CompletionStage<ComputerEngineHealth> check();
}
