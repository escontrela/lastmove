package com.escontrela.lastmove.ui.component.status;

import com.escontrela.lastmove.ui.component.toolbar.ThemeIcon;
import com.escontrela.lastmove.ui.service.BloodPressureWindowService;
import javafx.scene.control.Button;

/** Compact status-rail indicator for an active Blood Pressure monitor. */
public final class BloodPressureStatusIndicator extends Button {

  public BloodPressureStatusIndicator(BloodPressureWindowService monitor) {
    setAccessibleText("Blood Pressure monitor active");
    setTooltip(new javafx.scene.control.Tooltip("Open Blood Pressure monitor (Ctrl+Shift+B)"));
    setGraphic(icon());
    setContentDisplay(javafx.scene.control.ContentDisplay.GRAPHIC_ONLY);
    setMinSize(28, 28);
    setPrefSize(28, 28);
    getStyleClass().add("blood-pressure-status-indicator");
    setOnAction(event -> monitor.show());
    setVisible(monitor.isActive());
    setManaged(monitor.isActive());
    monitor.activeProperty().addListener((ignored, oldValue, active) -> {
      setVisible(active);
      setManaged(active);
    });
  }

  private ThemeIcon icon() {
    ThemeIcon icon = new ThemeIcon();
    icon.setFitWidth(18);
    icon.setFitHeight(18);
    icon.setLightIconResource("/images/blood_pressure_35dp_000000.png");
    icon.setDarkIconResource("/images/blood_pressure_35dp_FFFFFF.png");
    return icon;
  }
}
