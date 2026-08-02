package com.escontrela.lastmove.ui.service;

import java.util.prefs.Preferences;
import org.springframework.stereotype.Component;

/** Stores preferences that affect the application startup experience. */
@Component
public class StartupPreferencesService {

    private static final String SPLASH_SCREEN_ENABLED_PREFERENCE = "splash-screen-enabled";

    private final Preferences preferences = Preferences.userNodeForPackage(StartupPreferencesService.class);

    public boolean isSplashScreenEnabled() {
        return preferences.getBoolean(SPLASH_SCREEN_ENABLED_PREFERENCE, true);
    }

    public void setSplashScreenEnabled(boolean enabled) {
        preferences.putBoolean(SPLASH_SCREEN_ENABLED_PREFERENCE, enabled);
    }
}
