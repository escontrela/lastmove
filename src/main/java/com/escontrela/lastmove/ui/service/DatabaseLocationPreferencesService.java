package com.escontrela.lastmove.ui.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.prefs.Preferences;
import org.springframework.stereotype.Component;

/** Stores and validates the SQLite database file selected by the user. */
@Component
public class DatabaseLocationPreferencesService {

    private static final String DATABASE_PATH_KEY = "database-file-path";
    private static final Preferences PREFERENCES =
            Preferences.userNodeForPackage(DatabaseLocationPreferencesService.class);

    /** Resolves to the same working-directory-relative file used by the original configuration. */
    public static Path defaultDatabasePath() {
        return Path.of("lastmove.db").toAbsolutePath().normalize();
    }

    /** Returns the selected path, or the original application-relative default. */
    public static Path configuredDatabasePath() {
        return normalize(PREFERENCES.get(DATABASE_PATH_KEY, defaultDatabasePath().toString()));
    }

    public Path currentDatabasePath() {
        return configuredDatabasePath();
    }

    /** Checks that the parent directory can be written before committing the preference. */
    public Path validatePath(String value) throws IOException {
        Path path = normalize(value);
        Path parent = path.getParent();
        if (parent == null) {
            throw new IllegalArgumentException("Choose a database file inside a directory.");
        }
        Files.createDirectories(parent);
        if (Files.exists(path)) {
            if (!Files.isRegularFile(path) || !Files.isWritable(path)) {
                throw new IllegalArgumentException("The selected database file is not writable.");
            }
        } else {
            Path probe = Files.createTempFile(parent, ".lastmove-write-check-", ".tmp");
            Files.delete(probe);
        }
        return path;
    }

    public void save(Path path) {
        PREFERENCES.put(DATABASE_PATH_KEY, path.toString());
        try {
            PREFERENCES.flush();
        } catch (java.util.prefs.BackingStoreException exception) {
            throw new IllegalStateException("Could not save the database location preference.", exception);
        }
    }

    /** Builds the SQLite URL while preserving the application's connection settings. */
    public static String configuredJdbcUrl() {
        String path = configuredDatabasePath().toString().replace('\\', '/');
        return "jdbc:sqlite:" + path
                + "?foreign_keys=on&busy_timeout=30000&journal_mode=WAL";
    }

    private static Path normalize(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Enter a database file path.");
        }
        return Path.of(value.trim()).toAbsolutePath().normalize();
    }
}
