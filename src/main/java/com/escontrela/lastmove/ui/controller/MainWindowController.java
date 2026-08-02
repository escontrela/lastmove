package com.escontrela.lastmove.ui.controller;

import com.escontrela.lastmove.ui.screen.UiFlowManager;
import com.escontrela.lastmove.ui.screen.UiScreenController;
import com.escontrela.lastmove.ui.screen.UiScreenId;
import javafx.fxml.FXML;
import javafx.event.ActionEvent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/** Controller for the LastMove landing screen. */
@Component
public class MainWindowController implements UiScreenController {

    private final UiFlowManager uiFlowManager;

    @FXML
    private BorderPane root;
    @FXML
    private Label featureStatusLabel;

    public MainWindowController(@Lazy UiFlowManager uiFlowManager) {
        this.uiFlowManager = uiFlowManager;
    }

    @FXML
    public void initialize() {
        root.getProperties().put("controller", this);
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
}
