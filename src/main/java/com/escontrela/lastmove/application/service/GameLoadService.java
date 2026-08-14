package com.escontrela.lastmove.application.service;

import com.escontrela.lastmove.application.dto.PgnImportRequest;
import com.escontrela.lastmove.infrastructure.chesspresso.ChesspressoPgnReader;
import com.escontrela.lastmove.domain.game.ImportedPgnGame;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Application service responsible for loading a chess game from a PGN source.
 *
 * <p>Delegates PGN parsing to the infrastructure layer and returns an engine-neutral imported
 * game. Session creation is intentionally owned by the caller's session workflow.
 */
@Service
public class GameLoadService {

    private final ChesspressoPgnReader pgnReader;

    public GameLoadService(ChesspressoPgnReader pgnReader) {
        this.pgnReader = Objects.requireNonNull(pgnReader, "pgnReader must not be null");
    }

    /** Parses a PGN source and returns its metadata, main line and variations. */
    public ImportedPgnGame importPgn(PgnImportRequest request) {
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
            return imported;
        } catch (PgnImportException exception) {
            throw new IllegalArgumentException("Unable to import PGN", exception.getCause());
        }
    }

    private static final class PgnImportException extends RuntimeException {
        private PgnImportException(Exception cause) { super(cause); }
    }
}
