package com.escontrela.lastmove.application.computer;

/** Creates fresh engine instances so each progressive game owns its process lifecycle. */
public interface ComputerMoveEngineProvider {

  ComputerEngineDescriptor descriptor();

  ComputerMoveEngine create();

  /** Conservative estimate used to admit one speculative worker beside a real CvC search. */
  default int estimatedSearchWorkers() {
    return Runtime.getRuntime().availableProcessors();
  }
}
