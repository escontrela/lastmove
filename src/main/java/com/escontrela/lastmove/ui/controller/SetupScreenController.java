package com.escontrela.lastmove.ui.controller;

import com.escontrela.lastmove.application.service.ComputerEngineSettingsService;
import com.escontrela.lastmove.ui.screen.UiFlowManager;
import com.escontrela.lastmove.ui.screen.UiScreenController;
import com.escontrela.lastmove.ui.screen.UiScreenId;
import com.escontrela.lastmove.ui.service.ApplicationThemeService;
import com.escontrela.lastmove.ui.service.StartupPreferencesService;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/** Controller for the application setup screen. */
@Component
public class SetupScreenController implements UiScreenController {

    private final UiFlowManager uiFlowManager;
    private final ApplicationThemeService themeService;
    private final StartupPreferencesService startupPreferencesService;
    private final ComputerEngineSettingsService computerEngineSettingsService;

    @FXML
    private BorderPane root;
    @FXML
    private CheckBox nightModeCheckBox;
    @FXML
    private CheckBox showSplashCheckBox;
    @FXML
    private Button applyButton;
    @FXML
    private TextField sunfishExecutablePathField;
    @FXML
    private Label sunfishValidationLabel;

    private boolean savedNightMode;
    private boolean savedSplashScreen;
    private String savedSunfishExecutablePath;

    public SetupScreenController(
            @Lazy UiFlowManager uiFlowManager,
            ApplicationThemeService themeService,
            StartupPreferencesService startupPreferencesService,
            ComputerEngineSettingsService computerEngineSettingsService) {
        this.uiFlowManager = uiFlowManager;
        this.themeService = themeService;
        this.startupPreferencesService = startupPreferencesService;
        this.computerEngineSettingsService = computerEngineSettingsService;
    }

    @FXML
    public void initialize() {
        root.getProperties().put("controller", this);
        applyButton.getStyleClass().addAll("message-box-button", "message-box-accept-button");
        nightModeCheckBox.selectedProperty().addListener((ignored, oldValue, newValue) ->
                updateApplyButtonVisibility());
        showSplashCheckBox.selectedProperty().addListener((ignored, oldValue, newValue) ->
                updateApplyButtonVisibility());
        sunfishExecutablePathField.textProperty().addListener((ignored, oldValue, newValue) -> {
            sunfishValidationLabel.setText("");
            updateApplyButtonVisibility();
        });
    }

    @Override
    public void onShow() {
        savedNightMode = themeService.currentThemeMode().isNightMode();
        savedSplashScreen = startupPreferencesService.isSplashScreenEnabled();
        savedSunfishExecutablePath = computerEngineSettingsService
                .sunfishSettings()
                .executablePath()
                .toString();
        nightModeCheckBox.setSelected(savedNightMode);
        showSplashCheckBox.setSelected(savedSplashScreen);
        sunfishExecutablePathField.setText(savedSunfishExecutablePath);
        sunfishValidationLabel.setText("");
        updateApplyButtonVisibility();
    }

    @FXML
    public void applySettings() {
        try {
            savedSunfishExecutablePath = computerEngineSettingsService
                    .updateSunfishExecutable(sunfishExecutablePathField.getText())
                    .executablePath()
                    .toString();
        } catch (IllegalArgumentException exception) {
            sunfishValidationLabel.setText(exception.getMessage());
            return;
        }
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
                || showSplashCheckBox.isSelected() != savedSplashScreen
                || !sunfishExecutablePathField.getText().trim().equals(savedSunfishExecutablePath);
        applyButton.setVisible(hasUnsavedChanges);
        applyButton.setManaged(hasUnsavedChanges);
    }
}
