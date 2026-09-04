package com.escontrela.lastmove.ui.component.toolbar;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.ListChangeListener;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

/** A non-interactive image that follows the application's light and dark themes. */
public final class ThemeIcon extends ImageView {
  private static final Map<String, Image> IMAGE_CACHE = new ConcurrentHashMap<>();

  private final StringProperty lightIconResource =
      new SimpleStringProperty(this, "lightIconResource", "");
  private final StringProperty darkIconResource =
      new SimpleStringProperty(this, "darkIconResource", "");
  private final ListChangeListener<String> rootStyleListener = change -> refreshIcon();
  private final ChangeListener<Parent> sceneRootListener =
      (ignored, oldRoot, newRoot) -> observeRoot(newRoot);
  private Parent observedRoot;
  private Scene observedScene;

  public ThemeIcon() {
    setFitWidth(20);
    setFitHeight(20);
    setPreserveRatio(true);
    setSmooth(true);
    lightIconResource.addListener((ignored, oldValue, newValue) -> refreshIcon());
    darkIconResource.addListener((ignored, oldValue, newValue) -> refreshIcon());
    sceneProperty().addListener((ignored, oldScene, newScene) -> observeScene(newScene));
  }

  public String getLightIconResource() { return lightIconResource.get(); }
  public void setLightIconResource(String resource) { lightIconResource.set(resource); }
  public StringProperty lightIconResourceProperty() { return lightIconResource; }

  public String getDarkIconResource() { return darkIconResource.get(); }
  public void setDarkIconResource(String resource) { darkIconResource.set(resource); }
  public StringProperty darkIconResourceProperty() { return darkIconResource; }

  private void observeScene(Scene scene) {
    if (observedScene != null) observedScene.rootProperty().removeListener(sceneRootListener);
    observedScene = scene;
    if (scene == null) observeRoot(null);
    else {
      scene.rootProperty().addListener(sceneRootListener);
      observeRoot(scene.getRoot());
    }
  }

  private void observeRoot(Parent root) {
    if (observedRoot != null) observedRoot.getStyleClass().removeListener(rootStyleListener);
    observedRoot = root;
    if (root != null) root.getStyleClass().addListener(rootStyleListener);
    refreshIcon();
  }

  private void refreshIcon() {
    String resource = ToolbarIconAssetResolver.resolve(
        observedRoot, getLightIconResource(), getDarkIconResource());
    if (resource == null || resource.isBlank()) {
      setImage(null);
      return;
    }
    setImage(IMAGE_CACHE.computeIfAbsent(resource, value -> {
      var url = ThemeIcon.class.getResource(value);
      if (url == null) throw new IllegalArgumentException("Icon resource not found: " + value);
      return new Image(url.toExternalForm(), true);
    }));
  }
}
