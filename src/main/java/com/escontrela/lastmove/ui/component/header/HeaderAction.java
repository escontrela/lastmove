package com.escontrela.lastmove.ui.component.header;

import java.util.Objects;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;

/** A contextual, icon-only action shown by {@link ApplicationHeader}. */
public record HeaderAction(
        String accessibleText,
        String tooltip,
        String lightIconResource,
        String darkIconResource,
        EventHandler<ActionEvent> onAction,
        boolean disabled) {

    public HeaderAction {
        accessibleText = requireText(accessibleText, "accessibleText");
        tooltip = requireText(tooltip, "tooltip");
        lightIconResource = requireText(lightIconResource, "lightIconResource");
        darkIconResource = requireText(darkIconResource, "darkIconResource");
        onAction = Objects.requireNonNull(onAction, "onAction must not be null");
    }

    private static String requireText(String value, String name) {
        String text = Objects.requireNonNull(value, name + " must not be null").trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return text;
    }
}
