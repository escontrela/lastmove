package com.escontrela.lastmove.ui.component.toolbar;

import javafx.scene.Parent;

/** Resolves the icon resource that matches the active application theme. */
public final class ToolbarIconAssetResolver {

    private static final String NIGHT_MODE_STYLE_CLASS = "night-mode";

    private ToolbarIconAssetResolver() {
    }

    public static String resolve(Parent root, String lightResource, String darkResource) {
        if (isNightMode(root) && darkResource != null && !darkResource.isBlank()) {
            return darkResource;
        }
        return lightResource;
    }

    public static boolean isNightMode(Parent root) {
        return root != null && root.getStyleClass().contains(NIGHT_MODE_STYLE_CLASS);
    }
}
