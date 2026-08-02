package com.escontrela.lastmove.ui.controller;

import com.escontrela.lastmove.ui.screen.UiFlowManager;
import com.escontrela.lastmove.ui.screen.UiScreenController;
import com.escontrela.lastmove.ui.screen.UiScreenId;
import com.escontrela.lastmove.ui.service.ApplicationThemeService;
import com.escontrela.lastmove.ui.service.StartupPreferencesService;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.layout.BorderPane;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/** Controller for the application setup screen. */
@Component
public class SetupScreenController implements UiScreenController {

    private final UiFlowManager uiFlowManager;
    private final ApplicationThemeService themeService;
    private final StartupPreferencesService startupPreferencesService;

    @FXML
    private BorderPane root;
    @FXML
    private CheckBox nightModeCheckBox;
    @FXML
    private CheckBox showSplashCheckBox;
    @FXML
    private Button applyButton;

    private boolean savedNightMode;
    private boolean savedSplashScreen;

    public SetupScreenController(
            @Lazy UiFlowManager uiFlowManager,
            ApplicationThemeService themeService,
            StartupPreferencesService startupPreferencesService) {
        this.uiFlowManager = uiFlowManager;
        this.themeService = themeService;
        this.startupPreferencesService = startupPreferencesService;
    }

    @FXML
    public void initialize() {
        root.getProperties().put("controller", this);
        nightModeCheckBox.selectedProperty().addListener((ignored, oldValue, newValue) ->
                updateApplyButtonVisibility());
        showSplashCheckBox.selectedProperty().addListener((ignored, oldValue, newValue) ->
                updateApplyButtonVisibility());
    }

    @Override
    public void onShow() {
        savedNightMode = themeService.currentThemeMode().isNightMode();
        savedSplashScreen = startupPreferencesService.isSplashScreenEnabled();
        nightModeCheckBox.setSelected(savedNightMode);
        showSplashCheckBox.setSelected(savedSplashScreen);
        updateApplyButtonVisibility();
    }

    @FXML
    public void applySettings() {
        themeService.setNightMode(nightModeCheckBox.isSelected());
        startupPreferencesService.setSplashScreenEnabled(showSplashCheckBox.isSelected());
        savedNightMode = nightModeCheckBox.isSelected();
        savedSplashScreen = showSplashCheckBox.isSelected();
        updateApplyButtonVisibility();
    }

    @FXML
    public void backToMain() {
        uiFlowManager.show(UiScreenId.MAIN);
    }

    private void updateApplyButtonVisibility() {
        boolean hasUnsavedChanges = nightModeCheckBox.isSelected() != savedNightMode
                || showSplashCheckBox.isSelected() != savedSplashScreen;
        applyButton.setVisible(hasUnsavedChanges);
        applyButton.setManaged(hasUnsavedChanges);
    }
}
