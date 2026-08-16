package com.escontrela.lastmove.ui.service;

import java.util.Objects;
import java.util.prefs.Preferences;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import org.springframework.stereotype.Component;

/** Stores the optional visual finish applied by reusable chess boards. */
@Component
public class BoardAppearancePreferencesService {

    private static final String BOARD_VISUAL_EFFECTS_PREFERENCE = "board-visual-effects-enabled";

    private final Preferences preferences;
    private final BooleanProperty boardVisualEffectsEnabled;

    public BoardAppearancePreferencesService() {
        this(Preferences.userNodeForPackage(BoardAppearancePreferencesService.class));
    }

    BoardAppearancePreferencesService(Preferences preferences) {
        this.preferences = Objects.requireNonNull(preferences, "preferences must not be null");
        this.boardVisualEffectsEnabled = new SimpleBooleanProperty(
                this,
                "boardVisualEffectsEnabled",
                preferences.getBoolean(BOARD_VISUAL_EFFECTS_PREFERENCE, true));
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
}
