package com.escontrela.lastmove.ui.component.header;

import java.util.Objects;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;

/** A single location in the application header breadcrumb trail. */
public record HeaderBreadcrumb(String label, EventHandler<ActionEvent> onAction) {

    public HeaderBreadcrumb {
        label = Objects.requireNonNull(label, "label must not be null").trim();
        if (label.isEmpty()) {
            throw new IllegalArgumentException("label must not be blank");
        }
    }

    public static HeaderBreadcrumb current(String label) {
        return new HeaderBreadcrumb(label, null);
    }

    public static HeaderBreadcrumb link(String label, EventHandler<ActionEvent> onAction) {
        return new HeaderBreadcrumb(label, Objects.requireNonNull(onAction, "onAction must not be null"));
    }

    public boolean isNavigable() {
        return onAction != null;
    }
}
