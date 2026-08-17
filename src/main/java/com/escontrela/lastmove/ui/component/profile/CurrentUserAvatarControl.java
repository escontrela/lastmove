package com.escontrela.lastmove.ui.component.profile;

import java.util.Objects;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Circle;

/** Compact, theme-aware monogram avatar for the current local player. */
public final class CurrentUserAvatarControl extends Button {

    private static final int TONE_COUNT = 5;

    private final StackPane avatarGraphic = new StackPane();
    private final Circle outerRing = new Circle(19.0);
    private final Circle avatarFace = new Circle(16.0);
    private final Label initialsLabel = new Label();
    private final StringProperty displayName = new SimpleStringProperty(this, "displayName", "");

    public CurrentUserAvatarControl() {
        getStyleClass().add("current-user-avatar");
        setContentDisplay(ContentDisplay.GRAPHIC_ONLY);

        avatarGraphic.setMinSize(42.0, 42.0);
        avatarGraphic.setPrefSize(42.0, 42.0);
        avatarGraphic.setMaxSize(42.0, 42.0);
        outerRing.getStyleClass().add("current-user-avatar-ring");
        avatarFace.getStyleClass().add("current-user-avatar-face");
        initialsLabel.getStyleClass().add("current-user-avatar-initials");
        avatarGraphic.getChildren().setAll(outerRing, avatarFace, initialsLabel);
        setGraphic(avatarGraphic);

        setMinSize(42.0, 42.0);
        setPrefSize(42.0, 42.0);
        setMaxSize(42.0, 42.0);
        displayName.addListener((ignored, oldName, newName) -> refresh());
        refresh();
    }

    public StringProperty displayNameProperty() {
        return displayName;
    }

    public String getDisplayName() {
        return displayName.get();
    }

    public void setDisplayName(String value) {
        displayName.set(value == null ? "" : value);
    }

    private void refresh() {
        String name = getDisplayName();
        initialsLabel.setText(CurrentUserAvatarText.initialsFor(name));
        getStyleClass().removeIf(style -> style.startsWith("current-user-avatar-tone-"));
        avatarFace.getStyleClass().removeIf(style -> style.startsWith("current-user-avatar-tone-"));
        int tone = Math.floorMod(Objects.requireNonNullElse(name, "").toLowerCase(java.util.Locale.ROOT).hashCode(), TONE_COUNT);
        String toneClass = "current-user-avatar-tone-" + tone;
        getStyleClass().add(toneClass);
        avatarFace.getStyleClass().add(toneClass);
        setTooltip(new Tooltip(initialsLabel.getText().equals("?") ? "Choose active player" : name));
    }
}
