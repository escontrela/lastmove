package com.escontrela.lastmove.ui.controller;

import com.escontrela.lastmove.ui.component.message.MessageBox;
import com.escontrela.lastmove.ui.component.context.ContextualMenuPanel;
import com.escontrela.lastmove.ui.screen.UiFlowManager;
import com.escontrela.lastmove.ui.screen.UiScreenController;
import com.escontrela.lastmove.ui.screen.UiScreenId;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.event.ActionEvent;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/** Controller for the LastMove landing screen. */
@Component
public class MainWindowController implements UiScreenController {

    private static final String NIGHT_MODE_STYLE_CLASS = "night-mode";
    private static final String LIGHT_LOGO_RESOURCE = "/images/lastmove-chess-logo.png";
    private static final String DARK_LOGO_RESOURCE = "/images/lastmove-chess-logo-dark.png";
    private static final Map<String, Image> IMAGE_CACHE = new ConcurrentHashMap<>();

    private final UiFlowManager uiFlowManager;

    @FXML
    private AnchorPane root;
    @FXML
    private Label featureStatusLabel;
    @FXML
    private ImageView statusBrandLogo;
    @FXML
    private ImageView pgnToolIcon;
    @FXML
    private ImageView fenToolIcon;
    @FXML
    private ImageView localGameToolIcon;
    @FXML
    private ImageView openingToolIcon;
    @FXML
    private ImageView trainingToolIcon;
    @FXML
    private ImageView engineToolIcon;
    @FXML
    private MessageBox startupMessageBox;
    @FXML
    private ContextualMenuPanel contextualMenuPanel;

    private final ListChangeListener<String> themeStyleListener = change -> updateThemeAssets();
    private boolean startupMessageShown;

    public MainWindowController(@Lazy UiFlowManager uiFlowManager) {
        this.uiFlowManager = uiFlowManager;
    }

    @FXML
    public void initialize() {
        root.getProperties().put("controller", this);
        root.getStyleClass().addListener(themeStyleListener);
        updateThemeAssets();
        configureContextMenu();
        startupMessageBox.setOnAccept(event -> openPgnAnalysis());
        startupMessageBox.setOnCancel(event ->
                featureStatusLabel.setText("Welcome to LastMove Chess."));
        startupMessageBox.setOnClose(event ->
                featureStatusLabel.setText("Welcome to LastMove Chess."));
    }

    @Override
    public void onShow() {
        if (!startupMessageShown) {
            startupMessageShown = true;
            startupMessageBox.show();
        }
    }

    @FXML
    public void openPgnAnalysis() {
        uiFlowManager.show(UiScreenId.PGN_ANALYSIS);
    }

    @FXML
    public void openSetup() {
        uiFlowManager.show(UiScreenId.SETUP);
    }

    @FXML
    public void showComingSoon(ActionEvent event) {
        String featureName = ((Button) event.getSource()).getAccessibleText();
        featureStatusLabel.setText(featureName + " is coming soon.");
    }

    @FXML
    public void showContextMenu(ContextMenuEvent event) {
        contextualMenuPanel.showAtScene(event.getSceneX(), event.getSceneY());
        event.consume();
    }

    private void configureContextMenu() {
        contextualMenuPanel.clearItems();
        contextualMenuPanel.addItem("Analyse a PGN", "", event -> openPgnAnalysis());
        contextualMenuPanel.addSeparator();
        contextualMenuPanel.addItem("Open setup", "", event -> openSetup());
        contextualMenuPanel.addItem("Dismiss welcome message", "Esc", event -> {
            startupMessageBox.hide();
            featureStatusLabel.setText("Welcome message dismissed.");
        });
        contextualMenuPanel.addSeparator();
        contextualMenuPanel.addItem("About LastMove Chess", "", event ->
                featureStatusLabel.setText("LastMove Chess — your chess study workspace."));
    }

    private void updateStatusBrandLogo() {
        String resource = isNightMode()
                ? DARK_LOGO_RESOURCE
                : LIGHT_LOGO_RESOURCE;
        statusBrandLogo.setImage(loadImage(resource));
    }

    private void updateThemeAssets() {
        updateStatusBrandLogo();
        String iconColor = isNightMode() ? "FFFFFF" : "000000";
        updateToolIcon(pgnToolIcon, "history", iconColor);
        updateToolIcon(fenToolIcon, "structure", iconColor);
        updateToolIcon(localGameToolIcon, "next", iconColor);
        updateToolIcon(openingToolIcon, "search", iconColor);
        updateToolIcon(trainingToolIcon, "filter", iconColor);
        updateToolIcon(engineToolIcon, "zoom", iconColor);
    }

    private boolean isNightMode() {
        return root.getStyleClass().contains(NIGHT_MODE_STYLE_CLASS);
    }

    private void updateToolIcon(ImageView imageView, String iconName, String iconColor) {
        imageView.setImage(loadImage("/images/" + iconName + "_35dp_" + iconColor + ".png"));
    }

    private Image loadImage(String resource) {
        return IMAGE_CACHE.computeIfAbsent(resource, path -> new Image(Objects.requireNonNull(
                getClass().getResource(path),
                () -> "Missing image resource: " + path).toExternalForm()));
    }
}
