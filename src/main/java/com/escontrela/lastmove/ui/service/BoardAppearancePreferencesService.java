package com.escontrela.lastmove.ui.service;

import com.escontrela.lastmove.ui.component.board.BoardAppearancePreset;
import java.util.Objects;
import java.util.prefs.Preferences;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import org.springframework.stereotype.Component;

/** Stores the optional visual finish applied by reusable chess boards. */
@Component
public class BoardAppearancePreferencesService {

    private static final String BOARD_VISUAL_EFFECTS_PREFERENCE = "board-visual-effects-enabled";
    private static final String BOARD_APPEARANCE_PRESET_PREFERENCE = "board-appearance-preset";

    private final Preferences preferences;
    private final BooleanProperty boardVisualEffectsEnabled;
    private final ObjectProperty<BoardAppearancePreset> boardAppearancePreset;

    public BoardAppearancePreferencesService() {
        this(Preferences.userNodeForPackage(BoardAppearancePreferencesService.class));
    }

    BoardAppearancePreferencesService(Preferences preferences) {
        this.preferences = Objects.requireNonNull(preferences, "preferences must not be null");
        this.boardVisualEffectsEnabled = new SimpleBooleanProperty(
                this,
                "boardVisualEffectsEnabled",
                preferences.getBoolean(BOARD_VISUAL_EFFECTS_PREFERENCE, true));
        this.boardAppearancePreset = new SimpleObjectProperty<>(
                this,
                "boardAppearancePreset",
                readAppearancePreset(preferences.get(BOARD_APPEARANCE_PRESET_PREFERENCE, null)));
    }

    public boolean isBoardVisualEffectsEnabled() {
        return boardVisualEffectsEnabled.get();
    }

    public BooleanProperty boardVisualEffectsEnabledProperty() {
        return boardVisualEffectsEnabled;
    }

    public void setBoardVisualEffectsEnabled(boolean enabled) {
        preferences.putBoolean(BOARD_VISUAL_EFFECTS_PREFERENCE, enabled);
        boardVisualEffectsEnabled.set(enabled);
    }

    public BoardAppearancePreset getBoardAppearancePreset() {
        return boardAppearancePreset.get();
    }

    public ObjectProperty<BoardAppearancePreset> boardAppearancePresetProperty() {
        return boardAppearancePreset;
    }

    public void setBoardAppearancePreset(BoardAppearancePreset preset) {
        BoardAppearancePreset requiredPreset = Objects.requireNonNull(preset, "preset must not be null");
        preferences.put(BOARD_APPEARANCE_PRESET_PREFERENCE, requiredPreset.name());
        boardAppearancePreset.set(requiredPreset);
    }

    private static BoardAppearancePreset readAppearancePreset(String value) {
        if (value == null || value.isBlank()) {
            return BoardAppearancePreset.STANDARD;
        }
        try {
            return BoardAppearancePreset.valueOf(value);
        } catch (IllegalArgumentException exception) {
            return BoardAppearancePreset.STANDARD;
        }
    }
}
