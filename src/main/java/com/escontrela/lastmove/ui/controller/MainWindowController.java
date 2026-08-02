package com.escontrela.lastmove.ui.controller;

import com.escontrela.lastmove.ui.component.message.MessageBox;
import com.escontrela.lastmove.ui.screen.UiFlowManager;
import com.escontrela.lastmove.ui.screen.UiScreenController;
import com.escontrela.lastmove.ui.screen.UiScreenId;
import java.util.Objects;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.event.ActionEvent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

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
    private ImageView statusBrandLogo;
    @FXML
    private MessageBox startupMessageBox;

    private final ListChangeListener<String> themeStyleListener = change -> updateStatusBrandLogo();
    private boolean startupMessageShown;

    public MainWindowController(@Lazy UiFlowManager uiFlowManager) {
        this.uiFlowManager = uiFlowManager;
    }

    @FXML
    public void initialize() {
        root.getProperties().put("controller", this);
        root.getStyleClass().addListener(themeStyleListener);
        updateStatusBrandLogo();
        startupMessageBox.setOnAccept(event -> openPgnAnalysis());
        startupMessageBox.setOnCancel(event ->
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

    private void updateStatusBrandLogo() {
        String resource = root.getStyleClass().contains(NIGHT_MODE_STYLE_CLASS)
                ? DARK_LOGO_RESOURCE
                : LIGHT_LOGO_RESOURCE;
        statusBrandLogo.setImage(new Image(Objects.requireNonNull(
                getClass().getResource(resource),
                () -> "Missing status logo resource: " + resource).toExternalForm()));
    }
}
