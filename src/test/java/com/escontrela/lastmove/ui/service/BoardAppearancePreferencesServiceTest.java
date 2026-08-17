package com.escontrela.lastmove.ui.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.prefs.Preferences;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class BoardAppearancePreferencesServiceTest {

    private final Preferences preferences = Preferences.userRoot().node("lastmove-test-" + UUID.randomUUID());

    @AfterEach
    void clearPreferences() throws Exception {
        preferences.removeNode();
    }

    @Test
    void enablesVisualEffectsByDefaultAndPersistsChanges() {
        BoardAppearancePreferencesService service = new BoardAppearancePreferencesService(preferences);

        assertTrue(service.isBoardVisualEffectsEnabled());

        service.setBoardVisualEffectsEnabled(false);

        assertFalse(service.isBoardVisualEffectsEnabled());
        assertFalse(new BoardAppearancePreferencesService(preferences).isBoardVisualEffectsEnabled());
    }
}
