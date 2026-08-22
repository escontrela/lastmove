package com.escontrela.lastmove.ui.component.header;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;

/** Immutable, declarative configuration for an {@link ApplicationHeader}. */
public final class HeaderConfiguration {

    private final boolean showBackButton;
    private final EventHandler<ActionEvent> onBack;
    private final List<HeaderBreadcrumb> breadcrumbs;
    private final boolean showStatistics;
    private final boolean showThemeToggle;
    private final EventHandler<ActionEvent> onStatistics;
    private final EventHandler<ActionEvent> onThemeToggle;
    private final EventHandler<ActionEvent> onAvatar;
    private final List<HeaderAction> contextActions;
    private final String currentUserName;

    private HeaderConfiguration(Builder builder) {
        showBackButton = builder.showBackButton;
        onBack = builder.onBack;
        breadcrumbs = List.copyOf(builder.breadcrumbs);
        showStatistics = builder.showStatistics;
        showThemeToggle = builder.showThemeToggle;
        onStatistics = builder.onStatistics;
        onThemeToggle = builder.onThemeToggle;
        onAvatar = builder.onAvatar;
        contextActions = List.copyOf(builder.contextActions);
        currentUserName = builder.currentUserName;
        if (showBackButton && onBack == null) {
            throw new IllegalStateException("A visible back button needs an action");
        }
    }

    public boolean showBackButton() { return showBackButton; }
    public EventHandler<ActionEvent> onBack() { return onBack; }
    public List<HeaderBreadcrumb> breadcrumbs() { return breadcrumbs; }
    public boolean showStatistics() { return showStatistics; }
    public boolean showThemeToggle() { return showThemeToggle; }
    public EventHandler<ActionEvent> onStatistics() { return onStatistics; }
    public EventHandler<ActionEvent> onThemeToggle() { return onThemeToggle; }
    public EventHandler<ActionEvent> onAvatar() { return onAvatar; }
    public List<HeaderAction> contextActions() { return contextActions; }
    public String currentUserName() { return currentUserName; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private boolean showBackButton;
        private EventHandler<ActionEvent> onBack;
        private final List<HeaderBreadcrumb> breadcrumbs = new ArrayList<>();
        private boolean showStatistics;
        private boolean showThemeToggle;
        private EventHandler<ActionEvent> onStatistics;
        private EventHandler<ActionEvent> onThemeToggle;
        private EventHandler<ActionEvent> onAvatar;
        private final List<HeaderAction> contextActions = new ArrayList<>();
        private String currentUserName = "";

        public Builder showBackButton(boolean value) { showBackButton = value; return this; }
        public Builder onBack(EventHandler<ActionEvent> value) { onBack = value; return this; }
        public Builder breadcrumbs(List<HeaderBreadcrumb> value) {
            breadcrumbs.clear(); breadcrumbs.addAll(Objects.requireNonNull(value, "breadcrumbs must not be null")); return this;
        }
        public Builder showStatistics(boolean value) { showStatistics = value; return this; }
        public Builder showThemeToggle(boolean value) { showThemeToggle = value; return this; }
        public Builder onStatistics(EventHandler<ActionEvent> value) { onStatistics = value; return this; }
        public Builder onThemeToggle(EventHandler<ActionEvent> value) { onThemeToggle = value; return this; }
        public Builder onAvatar(EventHandler<ActionEvent> value) { onAvatar = value; return this; }
        public Builder contextActions(List<HeaderAction> value) {
            contextActions.clear(); contextActions.addAll(Objects.requireNonNull(value, "contextActions must not be null")); return this;
        }
        public Builder currentUserName(String value) { currentUserName = Objects.requireNonNullElse(value, ""); return this; }
        public HeaderConfiguration build() { return new HeaderConfiguration(this); }
    }
}
