package com.escontrela.lastmove.ui.controller;

import com.escontrela.lastmove.ui.screen.UiFlowManager;
import com.escontrela.lastmove.ui.screen.UiScreenController;
import com.escontrela.lastmove.ui.screen.UiScreenId;
import com.escontrela.lastmove.ui.service.ApplicationThemeService;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.layout.BorderPane;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/** Controller for the application setup screen. */
@Component
public class SetupScreenController implements UiScreenController {

    private final UiFlowManager uiFlowManager;
    private final ApplicationThemeService themeService;

    @FXML
    private BorderPane root;
    @FXML
    private CheckBox nightModeCheckBox;

    public SetupScreenController(
            @Lazy UiFlowManager uiFlowManager, ApplicationThemeService themeService) {
        this.uiFlowManager = uiFlowManager;
        this.themeService = themeService;
    }

    @FXML
    public void initialize() {
        root.getProperties().put("controller", this);
    }

    @Override
    public void onShow() {
        nightModeCheckBox.setSelected(themeService.currentThemeMode().isNightMode());
    }

    @FXML
    public void applySettings() {
        themeService.setNightMode(nightModeCheckBox.isSelected());
    }

    @FXML
    public void backToMain() {
        uiFlowManager.show(UiScreenId.MAIN);
    }
}
