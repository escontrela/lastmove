package com.escontrela.lastmove.application.computer;

/** Creates fresh engine instances so each progressive game owns its process lifecycle. */
public interface ComputerMoveEngineProvider {

  ComputerEngineDescriptor descriptor();

  ComputerMoveEngine create();
}
