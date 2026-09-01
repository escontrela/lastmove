package com.escontrela.lastmove.ui.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.escontrela.lastmove.ui.component.board.BoardAppearancePreset;
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

    @Test
    void usesStandardAppearanceByDefaultAndPersistsTheV2Selection() {
        BoardAppearancePreferencesService service = new BoardAppearancePreferencesService(preferences);

        assertEquals(BoardAppearancePreset.STANDARD, service.getBoardAppearancePreset());

        service.setBoardAppearancePreset(BoardAppearancePreset.V2);

        assertEquals(BoardAppearancePreset.V2, service.getBoardAppearancePreset());
        assertEquals(
                BoardAppearancePreset.V2,
                new BoardAppearancePreferencesService(preferences).getBoardAppearancePreset());
    }

    @Test
    void fallsBackToStandardAppearanceWhenTheStoredValueIsInvalid() {
        preferences.put("board-appearance-preset", "RETIRED_STYLE");

        assertEquals(
                BoardAppearancePreset.STANDARD,
                new BoardAppearancePreferencesService(preferences).getBoardAppearancePreset());
    }
}
