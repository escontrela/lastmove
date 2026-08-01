package com.escontrela.lastmove.infrastructure.support;

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
}
