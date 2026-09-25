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
    private static final String LOOK_PREFERENCE = "application-look";
    public enum Look { CLASSIC, MODERN }

    private final Preferences preferences = Preferences.userNodeForPackage(ApplicationThemeService.class);
    private final Set<Parent> registeredRoots =
            Collections.newSetFromMap(new WeakHashMap<>());

    public void register(Parent root) {
        if (root == null) {
            return;
        }
        registeredRoots.add(root);
        applyTheme(root, currentThemeMode());
        applyLook(root, currentLook());
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
        Look look = currentLook();
        registeredRoots.forEach(root -> { applyTheme(root, themeMode); applyLook(root, look); });
    }

    public Look currentLook() {
        try { return Look.valueOf(preferences.get(LOOK_PREFERENCE, Look.CLASSIC.name())); }
        catch (IllegalArgumentException ignored) { return Look.CLASSIC; }
    }

    public void setLook(Look look) {
        preferences.put(LOOK_PREFERENCE, java.util.Objects.requireNonNull(look).name());
        refreshRegisteredRoots();
    }

    private void applyTheme(Parent root, ApplicationThemeMode mode) {
        root.getStyleClass().remove(NIGHT_MODE_STYLE_CLASS);
        if (mode.isNightMode()) {
            root.getStyleClass().add(NIGHT_MODE_STYLE_CLASS);
        }
    }

    private void applyLook(Parent root, Look look) {
        root.getStyleClass().removeAll("look-classic", "look-modern");
        root.getStyleClass().add(look == Look.MODERN ? "look-modern" : "look-classic");
    }
}
