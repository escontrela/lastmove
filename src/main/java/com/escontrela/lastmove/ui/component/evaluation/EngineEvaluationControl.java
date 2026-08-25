package com.escontrela.lastmove.ui.component.evaluation;

import com.escontrela.lastmove.application.dto.EngineEvaluationState;
import java.util.Objects;
import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/** Reusable, presentation-only card for one engine evaluation state. */
public final class EngineEvaluationControl extends VBox {

  private final Label engineMark = new Label("♞");
  private final Label engineName = new Label();
  private final Label engineVersion = new Label();
  private final Label score = metricValue();
  private final Label depth = metricValue();
  private final Label bestMove = metricValue();
  private final Label nodes = metricValue();
  private final Label compactEngineName = new Label();
  private final Label compactBestMove = new Label();
  private final VBox searchingBox = new VBox(6);
  private final Label searchStatus = new Label();
  private final Region activity = new Region();
  private final FadeTransition activityPulse;
  private final VBox expandedContent;
  private final HBox compactContent;
  private EventHandler<ActionEvent> onChangeEngine;
  private VBox host;
  private Insets expandedHostPadding;
  private boolean minimized;

  public EngineEvaluationControl() {
    getStyleClass().add("engine-evaluation-control");
    setSpacing(14);
    setFillWidth(true);

    engineMark.getStyleClass().add("engine-evaluation-mark");
    engineName.getStyleClass().add("engine-evaluation-name");
    engineVersion.getStyleClass().add("engine-evaluation-version");
    VBox title = new VBox(2, engineName, engineVersion);
    Region spacer = new Region();
    HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
    Button change = new Button("Change");
    change.getStyleClass().add("engine-evaluation-change");
    change.setOnAction(event -> { if (onChangeEngine != null) onChangeEngine.handle(event); });
    Button minimize = new Button("−");
    minimize.getStyleClass().add("engine-evaluation-minimize");
    minimize.setAccessibleText("Minimize engine evaluation");
    minimize.setOnAction(event -> setMinimized(true));
    HBox header = new HBox(9, engineMark, title, spacer, change, minimize);
    header.setAlignment(Pos.CENTER_LEFT);

    GridPane metrics = new GridPane();
    metrics.setHgap(22);
    metrics.setVgap(13);
    metrics.add(metric("Evaluation", score), 0, 0);
    metrics.add(metric("Depth", depth), 1, 0);
    metrics.add(metric("Best move", bestMove), 0, 1);
    metrics.add(metric("Nodes", nodes), 1, 1);
    GridPane.setHgrow(metrics.getChildren().get(0), javafx.scene.layout.Priority.ALWAYS);

    searchStatus.getStyleClass().add("engine-evaluation-searching");
    activity.getStyleClass().add("engine-evaluation-activity");
    activity.setMinHeight(4);
    activity.setPrefHeight(4);
    activity.setMaxWidth(Double.MAX_VALUE);
    searchingBox.getStyleClass().add("engine-evaluation-search-box");
    searchingBox.getChildren().addAll(searchStatus, activity);
    activityPulse = new FadeTransition(Duration.millis(800), activity);
    activityPulse.setFromValue(.3);
    activityPulse.setToValue(1);
    activityPulse.setAutoReverse(true);
    activityPulse.setCycleCount(Animation.INDEFINITE);

    expandedContent = new VBox(14, header, metrics, searchingBox);
    compactEngineName.getStyleClass().add("engine-evaluation-compact-name");
    compactBestMove.getStyleClass().add("engine-evaluation-compact-best-move");
    Label compactLabel = new Label("Best move");
    compactLabel.getStyleClass().add("engine-evaluation-compact-label");
    Region compactSpacer = new Region();
    HBox.setHgrow(compactSpacer, javafx.scene.layout.Priority.ALWAYS);
    Button maximize = new Button("⌃");
    maximize.getStyleClass().add("engine-evaluation-minimize");
    maximize.setAccessibleText("Maximize engine evaluation");
    maximize.setOnAction(event -> setMinimized(false));
    compactContent =
        new HBox(8, new Label("♞"), compactEngineName, compactSpacer, compactLabel, compactBestMove, maximize);
    compactContent.getStyleClass().add("engine-evaluation-compact-content");
    compactContent.setAlignment(Pos.CENTER_LEFT);

    getChildren().add(expandedContent);
    parentProperty().addListener((observable, oldParent, newParent) -> captureHost(newParent));
    render(new EngineEvaluationState(
        new com.escontrela.lastmove.application.computer.ComputerEngineDescriptor("pending", "Engine", "—"),
        java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.empty(), false));
  }

  public void setOnChangeEngine(EventHandler<ActionEvent> handler) { onChangeEngine = handler; }

  public void render(EngineEvaluationState state) {
    EngineEvaluationState required = Objects.requireNonNull(state, "state");
    engineName.setText(required.engine().displayName());
    compactEngineName.setText(required.engine().displayName());
    engineVersion.setText(required.engine().version());
    score.setText(required.score().orElse("—"));
    depth.setText(required.depth().map(String::valueOf).orElse("—"));
    bestMove.setText(required.bestMove().orElse("—"));
    compactBestMove.setText(required.bestMove().orElse("—"));
    nodes.setText(required.nodes().map(EngineEvaluationControl::formatNodes).orElse("—"));
    searchStatus.setText(required.searching() ? "Searching…" : "Waiting");
    searchingBox.setManaged(true);
    searchingBox.setVisible(true);
    activity.getStyleClass().remove("engine-evaluation-activity-idle");
    if (required.searching()) {
      activityPulse.play();
    } else {
      activityPulse.stop();
      activity.setOpacity(1);
      activity.getStyleClass().add("engine-evaluation-activity-idle");
    }
  }

  /** Toggles the compact bottom-bar presentation without stopping the engine analysis. */
  public void setMinimized(boolean value) {
    if (minimized == value) return;
    minimized = value;
    getChildren().setAll(value ? compactContent : expandedContent);
    setSpacing(value ? 0 : 14);
    setMinHeight(USE_PREF_SIZE);
    setPrefHeight(USE_COMPUTED_SIZE);
    setMaxHeight(USE_PREF_SIZE);
    applyHostLayout();
  }

  public boolean isMinimized() {
    return minimized;
  }

  private void captureHost(javafx.scene.Parent parent) {
    if (parent instanceof VBox container) {
      host = container;
      expandedHostPadding = container.getPadding();
      applyHostLayout();
    } else {
      host = null;
      expandedHostPadding = null;
    }
  }

  private void applyHostLayout() {
    if (host == null) return;
    if (minimized) {
      if (!host.getStyleClass().contains("engine-evaluation-host-minimized")) {
        host.getStyleClass().add("engine-evaluation-host-minimized");
      }
      host.setPadding(Insets.EMPTY);
    } else {
      host.getStyleClass().remove("engine-evaluation-host-minimized");
      if (expandedHostPadding != null) host.setPadding(expandedHostPadding);
    }
  }

  private static VBox metric(String label, Label value) {
    Label title = new Label(label);
    title.getStyleClass().add("engine-evaluation-metric-label");
    VBox box = new VBox(3, title, value);
    box.getStyleClass().add("engine-evaluation-metric");
    return box;
  }

  private static Label metricValue() {
    Label label = new Label("—");
    label.getStyleClass().add("engine-evaluation-metric-value");
    return label;
  }

  private static String formatNodes(long value) {
    if (value >= 1_000_000) return String.format(java.util.Locale.ROOT, "%.1fM", value / 1_000_000.0);
    if (value >= 1_000) return String.format(java.util.Locale.ROOT, "%.1fK", value / 1_000.0);
    return Long.toString(value);
  }
}
