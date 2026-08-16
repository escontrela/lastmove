package com.escontrela.lastmove.ui.support;

import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.Optional;

/**
 * Factory that creates pre-configured {@link FileChooser} dialogs for the LastMove UI.
 *
 * <p>Keeps file-dialog configuration and platform boilerplate out of FXML controllers.
 */
@Component
public class FileChooserFactory {

    /**
     * Opens a PGN file chooser dialog.
     *
     * @param owner the owning window (may be {@code null})
     * @return the selected file, or empty if the dialog was cancelled
     */
    public Optional<File> choosePgnFile(Window owner) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Open PGN File");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("PGN Files", "*.pgn"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );
        return Optional.ofNullable(chooser.showOpenDialog(owner));
    }

    /**
     * Opens the operating system's save dialog for a PGN export.
     *
     * @param owner the owning window (may be {@code null})
     * @param suggestedName title used to propose a safe file name
     * @return the selected file with a {@code .pgn} extension, or empty when cancelled
     */
    public Optional<File> choosePgnExportFile(Window owner, String suggestedName) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export PGN");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("PGN Files", "*.pgn"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );
        chooser.setInitialFileName(safePgnFileName(suggestedName));
        return Optional.ofNullable(chooser.showSaveDialog(owner)).map(this::withPgnExtension);
    }

    private String safePgnFileName(String suggestedName) {
        String safe = Optional.ofNullable(suggestedName).orElse("analysis")
                .trim()
                .replaceAll("[\\\\/:*?\"<>|]", "-");
        safe = safe.isBlank() ? "analysis" : safe;
        return safe.toLowerCase(java.util.Locale.ROOT).endsWith(".pgn") ? safe : safe + ".pgn";
    }

    private File withPgnExtension(File file) {
        return file.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".pgn")
                ? file
                : new File(file.getAbsolutePath() + ".pgn");
    }
}
