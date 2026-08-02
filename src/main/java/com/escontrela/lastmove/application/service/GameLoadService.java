package com.escontrela.lastmove.application.service;

import com.escontrela.lastmove.application.dto.GameLoadResult;
import com.escontrela.lastmove.application.dto.PgnImportRequest;
import com.escontrela.lastmove.application.event.GameLoadedEvent;
import com.escontrela.lastmove.application.event.PgnImportFailedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * Application service responsible for loading a chess game from a PGN source.
 *
 * <p>Delegates PGN parsing to the infrastructure layer and publishes application events
 * so that the UI can react without direct coupling.
 */
@Service
public class GameLoadService {

    private final ApplicationEventPublisher eventPublisher;

    public GameLoadService(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    /**
     * Loads a game from the given PGN import request.
     *
     * <p>On success, publishes a {@link GameLoadedEvent}.
     * On failure, publishes a {@link PgnImportFailedEvent}.
     *
     * @param request the import request containing the PGN source
     * @return a result describing success or failure
     */
    public GameLoadResult load(PgnImportRequest request) {
        // TODO: delegate to ChesspressoPgnReader in the infrastructure layer
        throw new UnsupportedOperationException("GameLoadService.load is not yet implemented");
    }
}
