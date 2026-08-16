package com.escontrela.lastmove.ui.controller;

import com.escontrela.lastmove.application.computer.ComputerEngineHealth;
import com.escontrela.lastmove.application.service.ComputerEngineHealthService;
import com.escontrela.lastmove.application.service.ComputerEngineSettingsService;
import com.escontrela.lastmove.ui.screen.UiFlowManager;
import com.escontrela.lastmove.ui.screen.UiScreenController;
import com.escontrela.lastmove.ui.screen.UiScreenId;
import com.escontrela.lastmove.ui.service.ApplicationThemeService;
import com.escontrela.lastmove.ui.service.StartupPreferencesService;
import javafx.application.Platform;
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
    private final ComputerEngineHealthService computerEngineHealthService;

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
    private Button testSunfishButton;
    @FXML
    private Label sunfishValidationLabel;

    private boolean savedNightMode;
    private boolean savedSplashScreen;
    private String savedSunfishExecutablePath;

    public SetupScreenController(
            @Lazy UiFlowManager uiFlowManager,
            ApplicationThemeService themeService,
            StartupPreferencesService startupPreferencesService,
            ComputerEngineSettingsService computerEngineSettingsService,
            ComputerEngineHealthService computerEngineHealthService) {
        this.uiFlowManager = uiFlowManager;
        this.themeService = themeService;
        this.startupPreferencesService = startupPreferencesService;
        this.computerEngineSettingsService = computerEngineSettingsService;
        this.computerEngineHealthService = computerEngineHealthService;
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
            clearSunfishValidation();
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
        clearSunfishValidation();
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
            showSunfishValidation(exception.getMessage(), false);
            return;
        }
        themeService.setNightMode(nightModeCheckBox.isSelected());
        startupPreferencesService.setSplashScreenEnabled(showSplashCheckBox.isSelected());
        savedNightMode = nightModeCheckBox.isSelected();
        savedSplashScreen = showSplashCheckBox.isSelected();
        updateApplyButtonVisibility();
    }

    /** Runs a non-blocking end-to-end UCI probe against the saved Sunfish executable. */
    @FXML
    public void testSunfishConnection() {
        testSunfishButton.setDisable(true);
        showSunfishValidation("Checking wrapper, Python runtime and UCI response…", null);
        computerEngineHealthService.checkSunfish().whenComplete((health, failure) ->
                Platform.runLater(() -> finishSunfishCheck(health, failure)));
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
        testSunfishButton.setDisable(hasUnsavedChanges || savedSunfishExecutablePath == null);
        if (hasUnsavedChanges
                && !sunfishExecutablePathField.getText().trim().equals(savedSunfishExecutablePath)) {
            showSunfishValidation("Apply the executable path before testing the connection.", null);
        }
    }

    private void finishSunfishCheck(ComputerEngineHealth health, Throwable failure) {
        if (failure != null) {
            Throwable cause = failure.getCause() == null ? failure : failure.getCause();
            showSunfishValidation("Sunfish check failed: " + cause.getMessage(), false);
        } else {
            showSunfishValidation(health.message(), health.available());
        }
        updateApplyButtonVisibility();
    }

    private void clearSunfishValidation() {
        sunfishValidationLabel.setText("");
        sunfishValidationLabel.getStyleClass().removeAll(
                "settings-validation-success", "settings-validation-error");
    }

    private void showSunfishValidation(String message, Boolean successful) {
        sunfishValidationLabel.setText(message == null ? "" : message);
        sunfishValidationLabel.getStyleClass().removeAll(
                "settings-validation-success", "settings-validation-error");
        if (successful != null) {
            sunfishValidationLabel.getStyleClass().add(
                    successful ? "settings-validation-success" : "settings-validation-error");
        }
    }
}
