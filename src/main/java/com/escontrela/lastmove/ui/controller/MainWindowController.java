package com.escontrela.lastmove.ui.controller;

import com.escontrela.lastmove.ui.screen.UiFlowManager;
import com.escontrela.lastmove.ui.screen.UiScreenController;
import com.escontrela.lastmove.ui.screen.UiScreenId;
import javafx.fxml.FXML;
import javafx.event.ActionEvent;
import javafx.collections.ListChangeListener;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Objects;

/** Controller for the LastMove landing screen. */
@Component
public class MainWindowController implements UiScreenController {

    private static final String NIGHT_MODE_STYLE_CLASS = "night-mode";
    private static final String LIGHT_LOGO_RESOURCE = "/images/lastmove-chess-logo.png";
    private static final String DARK_LOGO_RESOURCE = "/images/lastmove-chess-logo-dark.png";

    private final UiFlowManager uiFlowManager;

    @FXML
    private BorderPane root;
    @FXML
    private Label featureStatusLabel;
    @FXML
    private ImageView brandLogo;

    private final ListChangeListener<String> themeStyleListener = change -> updateBrandLogo();

    public MainWindowController(@Lazy UiFlowManager uiFlowManager) {
        this.uiFlowManager = uiFlowManager;
    }

    @FXML
    public void initialize() {
        root.getProperties().put("controller", this);
        root.getStyleClass().addListener(themeStyleListener);
        updateBrandLogo();
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

    private void updateBrandLogo() {
        boolean nightMode = root.getStyleClass().contains(NIGHT_MODE_STYLE_CLASS);
        String resource = nightMode ? DARK_LOGO_RESOURCE : LIGHT_LOGO_RESOURCE;
        brandLogo.setImage(new Image(Objects.requireNonNull(
                getClass().getResource(resource),
                () -> "Missing brand logo resource: " + resource).toExternalForm()));
    }
}
