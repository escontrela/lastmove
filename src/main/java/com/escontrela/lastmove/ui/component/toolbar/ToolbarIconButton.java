package com.escontrela.lastmove.ui.component.toolbar;

import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.BooleanPropertyBase;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.ListChangeListener;
import javafx.css.PseudoClass;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

/**
 * A theme-aware, image-only toolbar button for JavaFX FXML screens.
 *
 * <p>The control detects the {@code night-mode} style class on the active scene root and swaps
 * its PNG asset automatically. It may also work as a toggle button through {@link #toggleModeProperty()}.
 */
public class ToolbarIconButton extends Button {

    private static final double BUTTON_SIZE = 40.0;
    private static final double ICON_SIZE = 18.0;
    private static final PseudoClass SELECTED_PSEUDO_CLASS =
            PseudoClass.getPseudoClass("toolbar-selected");
    private static final Map<String, Image> IMAGE_CACHE = new ConcurrentHashMap<>();

    private final ImageView iconView = new ImageView();
    private final StringProperty lightIconResource =
            new SimpleStringProperty(this, "lightIconResource", "");
    private final StringProperty darkIconResource =
            new SimpleStringProperty(this, "darkIconResource", "");
    private final StringProperty tooltipText = new SimpleStringProperty(this, "tooltipText", "");
    private final BooleanProperty toggleMode = new SimpleBooleanProperty(this, "toggleMode", false);
    private final BooleanProperty selected = new BooleanPropertyBase(false) {
        @Override
        protected void invalidated() {
            pseudoClassStateChanged(SELECTED_PSEUDO_CLASS, get());
        }

        @Override
        public Object getBean() {
            return ToolbarIconButton.this;
        }

        @Override
        public String getName() {
            return "selected";
        }
    };

    private final ListChangeListener<String> rootStyleClassListener = ignored -> refreshIcon();
    private final ChangeListener<Parent> sceneRootListener =
            (ignored, oldRoot, newRoot) -> observeRoot(newRoot);
    private Parent observedRoot;
    private Scene observedScene;

    public ToolbarIconButton() {
        getStyleClass().add("toolbar-icon-button");
        setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        setMinSize(BUTTON_SIZE, BUTTON_SIZE);
        setPrefSize(BUTTON_SIZE, BUTTON_SIZE);
        setMaxSize(BUTTON_SIZE, BUTTON_SIZE);

        iconView.setFitWidth(ICON_SIZE);
        iconView.setFitHeight(ICON_SIZE);
        iconView.setPreserveRatio(true);
        iconView.setSmooth(true);
        setGraphic(iconView);

        lightIconResource.addListener((ignored, oldValue, newValue) -> refreshIcon());
        darkIconResource.addListener((ignored, oldValue, newValue) -> refreshIcon());
        tooltipText.addListener((ignored, oldValue, newValue) -> refreshTooltip());
        textProperty().addListener((ignored, oldValue, newValue) -> refreshTextMode());
        sceneProperty().addListener((ignored, oldScene, newScene) -> observeScene(newScene));
        refreshTextMode();
    }

    /** Sets the icon dimensions for controls that need a more compact visual treatment. */
    public void setIconSize(double size) {
        if (size <= 0) {
            throw new IllegalArgumentException("size must be positive");
        }
        iconView.setFitWidth(size);
        iconView.setFitHeight(size);
    }

    private void refreshTextMode() {
        boolean labeled = getText() != null && !getText().isBlank();
        setContentDisplay(labeled ? ContentDisplay.LEFT : ContentDisplay.GRAPHIC_ONLY);
        setGraphicTextGap(labeled ? 8 : 0);
    }

    @Override
    public void fire() {
        if (isDisabled()) {
            return;
        }
        if (isToggleMode()) {
            setSelected(!isSelected());
        }
        super.fire();
    }

    public final StringProperty lightIconResourceProperty() {
        return lightIconResource;
    }

    public final String getLightIconResource() {
        return lightIconResource.get();
    }

    public final void setLightIconResource(String resource) {
        lightIconResource.set(resource);
    }

    public final StringProperty darkIconResourceProperty() {
        return darkIconResource;
    }

    public final String getDarkIconResource() {
        return darkIconResource.get();
    }

    public final void setDarkIconResource(String resource) {
        darkIconResource.set(resource);
    }

    public final StringProperty tooltipTextProperty() {
        return tooltipText;
    }

    public final String getTooltipText() {
        return tooltipText.get();
    }

    public final void setTooltipText(String text) {
        tooltipText.set(text);
    }

    public final BooleanProperty toggleModeProperty() {
        return toggleMode;
    }

    public final boolean isToggleMode() {
        return toggleMode.get();
    }

    public final void setToggleMode(boolean enabled) {
        toggleMode.set(enabled);
    }

    public final BooleanProperty selectedProperty() {
        return selected;
    }

    public final boolean isSelected() {
        return selected.get();
    }

    public final void setSelected(boolean value) {
        selected.set(value);
    }

    private void observeScene(Scene scene) {
        if (observedScene != null) {
            observedScene.rootProperty().removeListener(sceneRootListener);
        }
        observedScene = scene;
        if (scene != null) {
            scene.rootProperty().addListener(sceneRootListener);
            observeRoot(scene.getRoot());
        } else {
            observeRoot(null);
        }
    }

    private void observeRoot(Parent root) {
        if (observedRoot != null) {
            observedRoot.getStyleClass().removeListener(rootStyleClassListener);
        }
        observedRoot = root;
        if (observedRoot != null) {
            observedRoot.getStyleClass().addListener(rootStyleClassListener);
        }
        refreshIcon();
    }

    private void refreshIcon() {
        String resource = ToolbarIconAssetResolver.resolve(
                observedRoot, getLightIconResource(), getDarkIconResource());
        iconView.setImage(resource == null || resource.isBlank() ? null : cachedImage(resource));
    }

    private void refreshTooltip() {
        String text = getTooltipText();
        setTooltip(text == null || text.isBlank() ? null : new Tooltip(text));
    }

    private static Image cachedImage(String resourcePath) {
        return IMAGE_CACHE.computeIfAbsent(resourcePath, ToolbarIconButton::loadImage);
    }

    private static Image loadImage(String resourcePath) {
        URL resource = ToolbarIconButton.class.getResource(resourcePath);
        if (resource == null) {
            throw new IllegalArgumentException("Toolbar icon resource not found: " + resourcePath);
        }
        return new Image(resource.toExternalForm());
    }
}
