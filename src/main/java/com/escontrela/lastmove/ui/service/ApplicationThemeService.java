package com.escontrela.lastmove.ui.service;

import com.escontrela.lastmove.ui.model.ApplicationThemeMode;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.prefs.Preferences;
import javafx.scene.Parent;
import org.springframework.stereotype.Component;

/** Stores and applies the shared day/night preference to all active screen roots. */
@Component
public class ApplicationThemeService {

    private static final String NIGHT_MODE_STYLE_CLASS = "night-mode";
    private static final String NIGHT_MODE_PREFERENCE = "night-mode";

    private final Preferences preferences = Preferences.userNodeForPackage(ApplicationThemeService.class);
    private final Set<Parent> registeredRoots =
            Collections.newSetFromMap(new WeakHashMap<>());

    public void register(Parent root) {
        if (root == null) {
            return;
        }
        registeredRoots.add(root);
        applyTheme(root, currentThemeMode());
    }

    public ApplicationThemeMode currentThemeMode() {
        return preferences.getBoolean(NIGHT_MODE_PREFERENCE, false)
                ? ApplicationThemeMode.NIGHT
                : ApplicationThemeMode.DAY;
    }

    public void setNightMode(boolean enabled) {
        preferences.putBoolean(NIGHT_MODE_PREFERENCE, enabled);
        refreshRegisteredRoots();
    }

    public void refreshRegisteredRoots() {
        ApplicationThemeMode themeMode = currentThemeMode();
        registeredRoots.forEach(root -> applyTheme(root, themeMode));
    }

    private void applyTheme(Parent root, ApplicationThemeMode mode) {
        root.getStyleClass().remove(NIGHT_MODE_STYLE_CLASS);
        if (mode.isNightMode()) {
            root.getStyleClass().add(NIGHT_MODE_STYLE_CLASS);
        }
    }
}
