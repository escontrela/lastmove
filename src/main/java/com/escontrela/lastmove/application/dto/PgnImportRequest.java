package com.escontrela.lastmove.application.dto;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Input data for a PGN import operation.
 *
 * <p>Carries either a file path or raw PGN text. At least one must be provided.
 */
public class PgnImportRequest {

    private final Path filePath;
    private final String rawPgn;

    private PgnImportRequest(Path filePath, String rawPgn) {
        this.filePath = filePath;
        this.rawPgn = rawPgn;
    }

    /** Creates a request to import from a file. */
    public static PgnImportRequest fromFile(Path filePath) {
        return new PgnImportRequest(Objects.requireNonNull(filePath), null);
    }

    /** Creates a request to import from a raw PGN string. */
    public static PgnImportRequest fromText(String rawPgn) {
        return new PgnImportRequest(null, Objects.requireNonNull(rawPgn));
    }

    public java.util.Optional<Path> getFilePath() {
        return java.util.Optional.ofNullable(filePath);
    }

    public java.util.Optional<String> getRawPgn() {
        return java.util.Optional.ofNullable(rawPgn);
    }
}
