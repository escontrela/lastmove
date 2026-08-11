package com.escontrela.lastmove.application.service;

import com.escontrela.lastmove.application.dto.GameLoadResult;
import com.escontrela.lastmove.application.dto.GameSessionSummary;
import com.escontrela.lastmove.application.dto.PgnImportRequest;
import com.escontrela.lastmove.application.event.GameLoadedEvent;
import com.escontrela.lastmove.application.event.PgnImportFailedEvent;
import com.escontrela.lastmove.infrastructure.chesspresso.ChesspressoPgnReader;
import java.util.Objects;
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
    private final ChesspressoPgnReader pgnReader;
    private final GameSessionService gameSessionService;

    public GameLoadService(
            ApplicationEventPublisher eventPublisher,
            ChesspressoPgnReader pgnReader,
            GameSessionService gameSessionService) {
        this.eventPublisher = eventPublisher;
        this.pgnReader = Objects.requireNonNull(pgnReader, "pgnReader must not be null");
        this.gameSessionService = Objects.requireNonNull(gameSessionService, "gameSessionService must not be null");
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

    /** Parses a PGN source, creates a populated analysis session and makes it active. */
    public GameSessionSummary openSession(PgnImportRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        try {
            var imported = request.getFilePath()
                    .map(path -> {
                        try {
                            return pgnReader.readImportedFirst(path);
                        } catch (Exception exception) {
                            throw new PgnImportException(exception);
                        }
                    })
                    .orElseGet(() -> request.getRawPgn()
                            .map(raw -> {
                                try {
                                    return pgnReader.readImportedFirst(raw);
                                } catch (Exception exception) {
                                    throw new PgnImportException(exception);
                                }
                            })
                            .orElseThrow(() -> new IllegalArgumentException("A PGN source is required")));
            return gameSessionService.createPgnSession(imported);
        } catch (PgnImportException exception) {
            throw new IllegalArgumentException("Unable to import PGN", exception.getCause());
        }
    }

    private static final class PgnImportException extends RuntimeException {
        private PgnImportException(Exception cause) { super(cause); }
    }
}
