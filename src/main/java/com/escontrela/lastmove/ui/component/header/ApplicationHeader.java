package com.escontrela.lastmove.ui.component.header;

import com.escontrela.lastmove.ui.component.profile.CurrentUserAvatarControl;
import com.escontrela.lastmove.ui.component.toolbar.ThemeIcon;
import com.escontrela.lastmove.ui.component.toolbar.ToolbarIconButton;
import com.escontrela.lastmove.ui.service.FadeEffectsService;
import java.util.Objects;
import java.util.Optional;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.util.Duration;

/** Persistent application chrome with branding, navigation, actions and the active user. */
public final class ApplicationHeader extends HBox {

  private final ToolbarIconButton backButton =
      iconButton("Go home", "Home", "/images/home_35dp_000000.png", "/images/home_35dp_FFFFFF.png");
  private final HBox breadcrumbs = new HBox(8.0);
  private final HBox contextActions = new HBox(8.0);
  private final ToolbarIconButton statisticsButton =
      iconButton(
          "Open statistics",
          "Statistics",
          "/images/bar_chart_35dp_000000.png",
          "/images/bar_chart_35dp_FFFFFF.png");
  private final ToolbarIconButton themeToggleButton =
      iconButton(
          "Toggle light or night mode",
          "Light / night mode",
          "/images/dark_mode_35dp_000000.png",
          "/images/dark_mode_35dp_FFFFFF.png");
  private final CurrentUserAvatarControl currentUserAvatar = new CurrentUserAvatarControl();
  private final TextField homeSearch = new TextField();
  private Timeline homeSearchWidthAnimation;

  public ApplicationHeader() {
    getStyleClass().add("application-header");
    setAlignment(Pos.CENTER_LEFT);
    setSpacing(16.0);

    Label brandName = new Label("LastMove");
    brandName.getStyleClass().add("application-header-brand-name");
    Label brandEdition = new Label("Chess");
    brandEdition.getStyleClass().add("application-header-brand-edition");
    HBox branding = new HBox(7.0, logo(), brandName, brandEdition);
    branding.setAlignment(Pos.CENTER_LEFT);
    branding.getStyleClass().add("application-header-branding");

    backButton.getStyleClass().add("application-header-back");
    backButton.setIconSize(16.0);
    compactHeaderButton(backButton);
    statisticsButton.setIconSize(18.0);
    themeToggleButton.setIconSize(18.0);
    compactHeaderButton(statisticsButton);
    compactHeaderButton(themeToggleButton);
    backButton.setAccessibleText("Go home");
    backButton.setVisible(false);
    backButton.setManaged(false);
    breadcrumbs.setAlignment(Pos.CENTER_LEFT);
    breadcrumbs.getStyleClass().add("application-header-breadcrumbs");
    breadcrumbs.setMaxWidth(Double.MAX_VALUE);
    HBox.setHgrow(breadcrumbs, Priority.ALWAYS);
    contextActions.setAlignment(Pos.CENTER);
    homeSearch.setPromptText("Search");
    homeSearch.setVisible(false);
    homeSearch.setManaged(false);
    homeSearch.setPrefWidth(190.0);
    homeSearch.setMinWidth(190.0);
    homeSearch.setMaxWidth(340.0);
    homeSearch.getStyleClass().add("application-header-home-search");
    HBox.setMargin(homeSearch, new Insets(0, 4, 0, 4));
    homeSearch.focusedProperty().addListener((ignored, wasFocused, focused) ->
        animateHomeSearchWidth(focused ? 320.0 : 190.0));
    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);

    // Keep the Home search anchored beside the action icons on the right;
    // the flexible spacer absorbs the remaining header width.
    getChildren()
        .addAll(
            branding,
            backButton,
            breadcrumbs,
            spacer,
            homeSearch,
            statisticsButton,
            themeToggleButton,
            contextActions,
            currentUserAvatar);
    configure(HeaderConfiguration.builder().build());
  }

  /** Shows the compact Home-only search field and forwards query changes. */
  public void setHomeSearchVisible(boolean visible, java.util.function.Consumer<String> listener) {
    homeSearch.setVisible(visible);
    homeSearch.setManaged(visible);
    homeSearch.textProperty().removeListener(homeSearchListener);
    if (visible && listener != null) {
      homeSearchListener = (obs, oldValue, newValue) -> listener.accept(newValue);
      homeSearch.textProperty().addListener(homeSearchListener);
    }
  }

  private javafx.beans.value.ChangeListener<String> homeSearchListener =
      (obs, oldValue, newValue) -> {};

  private void animateHomeSearchWidth(double targetWidth) {
    if (homeSearchWidthAnimation != null) {
      homeSearchWidthAnimation.stop();
    }
    homeSearchWidthAnimation = new Timeline(
        new KeyFrame(Duration.millis(180),
            new KeyValue(homeSearch.prefWidthProperty(), targetWidth)));
    homeSearchWidthAnimation.play();
  }

  public void configure(HeaderConfiguration configuration) {
    Objects.requireNonNull(configuration, "configuration must not be null");
    backButton.setVisible(configuration.showBackButton());
    backButton.setManaged(configuration.showBackButton());
    backButton.setOnAction(configuration.onBack());
    rebuildBreadcrumbs(configuration.breadcrumbs());
    rebuildContextActions(configuration.contextActions());
    configureAction(statisticsButton, configuration.showStatistics(), configuration.onStatistics());
    configureAction(
        themeToggleButton, configuration.showThemeToggle(), configuration.onThemeToggle());
    currentUserAvatar.setDisplayName(configuration.currentUserName());
    currentUserAvatar.setPhoto(configuration.currentUserPhoto());
    currentUserAvatar.setOnAction(configuration.onAvatar());
  }

  /**
   * Replaces only the contextual actions while preserving the current navigation and user state.
   */
  public void setContextActions(java.util.List<HeaderAction> actions) {
    rebuildContextActions(Objects.requireNonNull(actions, "actions must not be null"));
  }

  /** Replaces only the back-button action, keeping its current visibility state. */
  public void setOnBack(javafx.event.EventHandler<javafx.event.ActionEvent> handler) {
    backButton.setOnAction(handler);
  }

  /** Updates the header avatar with a short cross-fade, preserving the rest of the chrome. */
  public void updateCurrentUser(String name, Optional<byte[]> photo, FadeEffectsService effects) {
    effects.fadeReplace(
        currentUserAvatar,
        () -> {
          currentUserAvatar.setDisplayName(name);
          currentUserAvatar.setPhoto(photo);
        });
  }

  private ImageView logo() {
    ImageView logo = new ImageView(loadLogo(false));
    logo.setFitWidth(48.0);
    logo.setFitHeight(48.0);
    logo.setPreserveRatio(true);
    logo.sceneProperty()
        .addListener(
            (ignored, oldScene, scene) -> {
              if (scene != null) {
                scene
                    .rootProperty()
                    .addListener(
                        (observable, oldRoot, root) ->
                            logo.setImage(loadLogo(root.getStyleClass().contains("night-mode"))));
                scene
                    .getRoot()
                    .getStyleClass()
                    .addListener(
                        (ListChangeListener<String>)
                            change ->
                                logo.setImage(
                                    loadLogo(
                                        scene.getRoot().getStyleClass().contains("night-mode"))));
                logo.setImage(loadLogo(scene.getRoot().getStyleClass().contains("night-mode")));
              }
            });
    logo.getStyleClass().add("application-header-logo");
    return logo;
  }

  private Image loadLogo(boolean nightMode) {
    String resource = "/images/logo/modern-logo-48.png";
    return new Image(
        Objects.requireNonNull(
                getClass().getResource(resource), () -> "Missing header logo: " + resource)
            .toExternalForm());
  }

  private void rebuildBreadcrumbs(java.util.List<HeaderBreadcrumb> entries) {
    breadcrumbs.getChildren().clear();
    for (int index = 0; index < entries.size(); index++) {
      HeaderBreadcrumb entry = entries.get(index);
      if (index > 0) {
        Label separator = new Label("›");
        separator.getStyleClass().add("application-header-breadcrumb-separator");
        breadcrumbs.getChildren().add(separator);
      }
      if (entry.isNavigable()) {
        Button link = new Button(entry.label());
        link.setOnAction(entry.onAction());
        configureBreadcrumbIcon(link, entry);
        link.getStyleClass().add("application-header-breadcrumb-link");
        breadcrumbs.getChildren().add(link);
      } else {
        Label current = new Label(entry.label());
        current.setEllipsisString("…");
        current.setMaxWidth(300.0);
        configureBreadcrumbIcon(current, entry);
        current.getStyleClass().add("application-header-breadcrumb-current");
        breadcrumbs.getChildren().add(current);
      }
    }
  }

  private void configureBreadcrumbIcon(
      javafx.scene.control.Labeled target, HeaderBreadcrumb entry) {
    if (entry.lightIconResource().isBlank() && entry.darkIconResource().isBlank()) return;
    ThemeIcon icon = new ThemeIcon();
    icon.setFitWidth(18.0);
    icon.setFitHeight(18.0);
    icon.setLightIconResource(entry.lightIconResource());
    icon.setDarkIconResource(entry.darkIconResource());
    target.setGraphic(icon);
    target.setGraphicTextGap(6.0);
  }

  private void rebuildContextActions(java.util.List<HeaderAction> actions) {
    contextActions
        .getChildren()
        .setAll(
            actions.stream()
                .map(
                    action -> {
                      ToolbarIconButton button =
                          iconButton(
                              action.accessibleText(),
                              action.tooltip(),
                              action.lightIconResource(),
                              action.darkIconResource());
                      button.setIconSize(18.0);
                      compactHeaderButton(button);
                      button.setOnAction(action.onAction());
                      button.setDisable(action.disabled());
                      return button;
                    })
                .toList());
  }

  private void configureAction(
      ToolbarIconButton button,
      boolean visible,
      javafx.event.EventHandler<javafx.event.ActionEvent> action) {
    button.setVisible(visible);
    button.setManaged(visible);
    button.setDisable(visible && action == null);
    button.setOnAction(action);
  }

  private static ToolbarIconButton iconButton(
      String accessibleText, String tooltip, String lightIcon, String darkIcon) {
    ToolbarIconButton button = new ToolbarIconButton();
    button.setAccessibleText(accessibleText);
    button.setTooltipText(tooltip);
    button.setLightIconResource(lightIcon);
    button.setDarkIconResource(darkIcon);
    return button;
  }

  private static void compactHeaderButton(ToolbarIconButton button) {
    button.setMinSize(40.0, 40.0);
    button.setPrefSize(40.0, 40.0);
    button.setMaxSize(40.0, 40.0);
    button.setPadding(new javafx.geometry.Insets(9.0));
  }
}
