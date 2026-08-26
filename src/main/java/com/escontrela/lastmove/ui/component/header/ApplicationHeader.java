package com.escontrela.lastmove.ui.component.header;

import com.escontrela.lastmove.ui.component.profile.CurrentUserAvatarControl;
import com.escontrela.lastmove.ui.component.toolbar.ToolbarIconButton;
import java.util.Objects;
import javafx.collections.ListChangeListener;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

/** Persistent application chrome with branding, navigation, actions and the active user. */
public final class ApplicationHeader extends HBox {

    private final Button backButton = new Button("‹");
    private final HBox breadcrumbs = new HBox(8.0);
    private final HBox contextActions = new HBox(8.0);
    private final ToolbarIconButton statisticsButton = iconButton(
            "Open statistics", "Statistics", "/images/bar_chart_35dp_000000.png", "/images/bar_chart_35dp_FFFFFF.png");
    private final ToolbarIconButton themeToggleButton = iconButton(
            "Toggle light or night mode", "Light / night mode", "/images/dark_mode_35dp_000000.png", "/images/dark_mode_35dp_FFFFFF.png");
    private final CurrentUserAvatarControl currentUserAvatar = new CurrentUserAvatarControl();

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
        backButton.setAccessibleText("Go back");
        backButton.setVisible(false);
        backButton.setManaged(false);
        breadcrumbs.setAlignment(Pos.CENTER_LEFT);
        breadcrumbs.getStyleClass().add("application-header-breadcrumbs");
        breadcrumbs.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(breadcrumbs, Priority.ALWAYS);
        contextActions.setAlignment(Pos.CENTER);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        getChildren().addAll(branding, backButton, breadcrumbs, spacer,
                statisticsButton, themeToggleButton, contextActions, currentUserAvatar);
        configure(HeaderConfiguration.builder().build());
    }

    public void configure(HeaderConfiguration configuration) {
        Objects.requireNonNull(configuration, "configuration must not be null");
        backButton.setVisible(configuration.showBackButton());
        backButton.setManaged(configuration.showBackButton());
        backButton.setOnAction(configuration.onBack());
        rebuildBreadcrumbs(configuration.breadcrumbs());
        rebuildContextActions(configuration.contextActions());
        configureAction(statisticsButton, configuration.showStatistics(), configuration.onStatistics());
        configureAction(themeToggleButton, configuration.showThemeToggle(), configuration.onThemeToggle());
        currentUserAvatar.setDisplayName(configuration.currentUserName());
        currentUserAvatar.setOnAction(configuration.onAvatar());
    }

    /** Replaces only the contextual actions while preserving the current navigation and user state. */
    public void setContextActions(java.util.List<HeaderAction> actions) {
        rebuildContextActions(Objects.requireNonNull(actions, "actions must not be null"));
    }

    /** Replaces only the back-button action, keeping its current visibility state. */
    public void setOnBack(javafx.event.EventHandler<javafx.event.ActionEvent> handler) {
        backButton.setOnAction(handler);
    }

    private ImageView logo() {
        ImageView logo = new ImageView(loadLogo(false));
        logo.setFitWidth(48.0);
        logo.setFitHeight(48.0);
        logo.setPreserveRatio(true);
        logo.sceneProperty().addListener((ignored, oldScene, scene) -> {
            if (scene != null) {
                scene.rootProperty().addListener((observable, oldRoot, root) ->
                        logo.setImage(loadLogo(root.getStyleClass().contains("night-mode"))));
                scene.getRoot().getStyleClass().addListener((ListChangeListener<String>) change ->
                        logo.setImage(loadLogo(scene.getRoot().getStyleClass().contains("night-mode"))));
                logo.setImage(loadLogo(scene.getRoot().getStyleClass().contains("night-mode")));
            }
        });
        logo.getStyleClass().add("application-header-logo");
        return logo;
    }

    private Image loadLogo(boolean nightMode) {
        String resource = nightMode ? "/images/last-move-logo-dark.png" : "/images/last-move-logo.png";
        return new Image(Objects.requireNonNull(getClass().getResource(resource),
                () -> "Missing header logo: " + resource).toExternalForm());
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
                link.getStyleClass().add("application-header-breadcrumb-link");
                breadcrumbs.getChildren().add(link);
            } else {
                Label current = new Label(entry.label());
                current.setEllipsisString("…");
                current.setMaxWidth(300.0);
                current.getStyleClass().add("application-header-breadcrumb-current");
                breadcrumbs.getChildren().add(current);
            }
        }
    }

    private void rebuildContextActions(java.util.List<HeaderAction> actions) {
        contextActions.getChildren().setAll(actions.stream().map(action -> {
            ToolbarIconButton button = iconButton(action.accessibleText(), action.tooltip(),
                    action.lightIconResource(), action.darkIconResource());
            button.setOnAction(action.onAction());
            button.setDisable(action.disabled());
            return button;
        }).toList());
    }

    private void configureAction(ToolbarIconButton button, boolean visible, javafx.event.EventHandler<javafx.event.ActionEvent> action) {
        button.setVisible(visible);
        button.setManaged(visible);
        button.setDisable(visible && action == null);
        button.setOnAction(action);
    }

    private static ToolbarIconButton iconButton(String accessibleText, String tooltip, String lightIcon, String darkIcon) {
        ToolbarIconButton button = new ToolbarIconButton();
        button.setAccessibleText(accessibleText);
        button.setTooltipText(tooltip);
        button.setLightIconResource(lightIcon);
        button.setDarkIconResource(darkIcon);
        return button;
    }
}
