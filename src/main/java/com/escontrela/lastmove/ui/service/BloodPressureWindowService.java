package com.escontrela.lastmove.ui.service;

import com.escontrela.lastmove.application.service.KnightshadeTelemetryService;
import com.knightshade.engine.api.SearchTelemetrySnapshot;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.time.Instant;
import com.escontrela.lastmove.ui.support.FileChooserFactory;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.util.Duration;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Lazy;

/** Owns the optional, non-modal Blood Pressure monitor window. */
@Component
@Lazy
public final class BloodPressureWindowService {

  private static final List<String> METRICS = List.of(
      "mainNodes", "qNodes", "TT hit / cutoff", "beta cutoff", "PVS re-search",
      "null / LMR", "aspiration retries", "evaluation cache", "workers", "stopReason");

  private final Stage primaryStage;
  private final ApplicationThemeService themeService;
  private final KnightshadeTelemetryService telemetryService;
  private final FileChooserFactory fileChooserFactory;
  private final BooleanProperty active = new SimpleBooleanProperty(this, "active");
  private final Map<String, javafx.scene.control.Label> liveValues = new HashMap<>();
  private final Map<String, javafx.scene.control.Label> maxValues = new HashMap<>();
  private final Map<String, Long> maxima = new HashMap<>();
  private final Map<String, BooleanProperty> parameterSelection = new HashMap<>();
  private final AtomicBoolean uiUpdatePending = new AtomicBoolean();
  private volatile SearchTelemetrySnapshot latestSnapshot;
  private long lastRenderNanos;
  private ComboBox<Integer> frequencySelector;
  private Stage monitorStage;
  private volatile Instant sessionStartedAt;

  public BloodPressureWindowService(Stage primaryStage, ApplicationThemeService themeService,
      KnightshadeTelemetryService telemetryService) {
    this(primaryStage, themeService, telemetryService, new FileChooserFactory());
  }

  @org.springframework.beans.factory.annotation.Autowired
  public BloodPressureWindowService(Stage primaryStage, ApplicationThemeService themeService,
      KnightshadeTelemetryService telemetryService, FileChooserFactory fileChooserFactory) {
    this.primaryStage = primaryStage;
    this.themeService = themeService;
    this.telemetryService = telemetryService;
    this.fileChooserFactory = fileChooserFactory;
    active.set(telemetryService.isEnabled());
    active.addListener((ignored, oldValue, newValue) -> telemetryService.setEnabled(newValue));
    telemetryService.subscribe(this::receiveSnapshot);
  }

  public BooleanProperty activeProperty() {
    return active;
  }

  public boolean isActive() {
    return active.get();
  }

  /** Toggles monitoring and opens or hides its floating configuration window. */
  public void toggle() {
    if (isActive()) {
      active.set(false);
      hide();
    } else {
      active.set(true);
      show();
    }
  }

  /** Opens the monitor without changing its enabled state. */
  public void show() {
    if (monitorStage == null) {
      monitorStage = createStage();
    }
    monitorStage.show();
    monitorStage.toFront();
  }

  /** Hides the window; closing it does not disable monitoring. */
  public void hide() {
    if (monitorStage != null) {
      monitorStage.hide();
    }
  }

  private Stage createStage() {
    Stage stage = new Stage();
    stage.setTitle("Blood Pressure");
    stage.initOwner(primaryStage);
    stage.initModality(Modality.NONE);
    stage.setResizable(true);
    stage.setOnCloseRequest(event -> {
      event.consume();
      stage.hide();
    });
    VBox root = content();
    themeService.register(root);
    Scene scene = new Scene(root, 760, 760);
    if (latestSnapshot != null) renderSnapshot(latestSnapshot);
    String stylesheet = getClass().getResource("/css/lastmove.css").toExternalForm();
    scene.getStylesheets().add(stylesheet);
    stage.setScene(scene);
    stage.setX(primaryStage.getX() + Math.max(20, primaryStage.getWidth() - 780));
    stage.setY(primaryStage.getY() + 80);
    return stage;
  }

  private VBox content() {
    VBox root = new VBox(12);
    root.setPadding(new Insets(20));
    root.setAlignment(Pos.TOP_LEFT);
    root.getStyleClass().addAll("app-shell", "blood-pressure-window");
    Label title = new Label("Blood Pressure");
    title.getStyleClass().add("app-title");
    Label subtitle = new Label("Knightshade search telemetry");
    subtitle.getStyleClass().add("hero-support");
    CheckBox enabled = new CheckBox("Enable monitoring");
    enabled.setSelected(true);
    enabled.selectedProperty().bindBidirectional(active);
    enabled.setOnAction(event -> {
      if (enabled.isSelected()) show();
      else hide();
    });
    Label frequencyLabel = new Label("Refresh frequency");
    frequencyLabel.getStyleClass().add("settings-field-label");
    frequencySelector = new ComboBox<>();
    frequencySelector.getItems().addAll(1, 2, 4, 10);
    frequencySelector.setValue(4);
    frequencySelector.setMaxWidth(Double.MAX_VALUE);
    GridPane parameters = new GridPane();
    parameters.setHgap(28); parameters.setVgap(6);
    GridPane liveMetrics = new GridPane();
    liveMetrics.setHgap(18);
    liveMetrics.setVgap(7);
    for (String metric : METRICS) {
      CheckBox checkBox = new CheckBox(metric);
      checkBox.setSelected(true);
      parameterSelection.put(metric, checkBox.selectedProperty());
      int parameterIndex = parameters.getChildren().size();
      parameters.add(checkBox, parameterIndex % 2, parameterIndex / 2);
      Label value = new Label("—");
      Label maximum = new Label("—");
      liveValues.put(metric, value);
      maxValues.put(metric, maximum);
      VBox card = new VBox(4, new Label(metric), value, new Label("MAX"), maximum);
      card.getStyleClass().add("blood-pressure-metric-card");
      liveMetrics.add(card, liveMetrics.getChildren().size() % 3, liveMetrics.getChildren().size() / 3);
      checkBox.selectedProperty().addListener((ignored, oldValue, selected) -> {
        card.setVisible(selected);
        card.setManaged(selected);
      });
    }
    Label parametersTitle = new Label("Parameters to monitor");
    parametersTitle.getStyleClass().add("card-title");
    Label liveTitle = new Label("Live metrics");
    liveTitle.getStyleClass().add("card-title");
    Label liveHint = new Label("Values appear when Knightshade is thinking.");
    liveHint.getStyleClass().add("hero-support");
    javafx.scene.control.Button export = new javafx.scene.control.Button("Export CSV");
    export.setOnAction(event -> exportCsv());
    export.disableProperty().bind(active.not());
    root.getChildren().addAll(title, subtitle, enabled, new Separator(), frequencyLabel,
        frequencySelector, parametersTitle, parameters, new Separator(), liveTitle, liveHint, liveMetrics, export);
    return root;
  }

  private void exportCsv() {
    var samples = telemetryService.samples();
    if (samples.isEmpty() || monitorStage == null) return;
    fileChooserFactory.chooseCsvExportFile(monitorStage, "knightshade-telemetry")
        .ifPresent(file -> {
          StringBuilder csv = new StringBuilder("sessionStartedAt,timestamp,depth,score,bestMove,elapsedMillis,parameters,mainNodes,qNodes,ttProbes,ttHits,ttCutoffs,betaCutoffs,pvsResearches,nullMoveAttempts,nullMoveCutoffs,lmrApplications,lmrResearches,aspirationRetries,evaluationCacheHits,evaluationCacheMisses,requestedWorkers,effectiveWorkers,stopReason\n");
          String parameters = parameterSelection.entrySet().stream().filter(e -> e.getValue().get()).map(Map.Entry::getKey).collect(java.util.stream.Collectors.joining("|"));
          java.time.Instant started = telemetryService.sessionStartedAt();
          for (SearchTelemetrySnapshot s : samples) {
            csv.append(started == null ? "" : started).append(',').append(started == null ? "" : started.plusMillis(s.elapsedMillis())).append(',').append(s.depth()).append(',').append(s.score()).append(',')
                .append(csvValue(s.bestMove())).append(',').append(s.elapsedMillis()).append(',').append(csvValue(parameters)).append(',')
                .append(s.mainNodes()).append(',').append(s.qNodes()).append(',').append(s.ttProbes()).append(',').append(s.ttHits()).append(',').append(s.ttCutoffs()).append(',')
                .append(s.betaCutoffs()).append(',').append(s.pvsResearches()).append(',').append(s.nullMoveAttempts()).append(',')
                .append(s.nullMoveCutoffs()).append(',').append(s.lmrApplications()).append(',').append(s.lmrResearches()).append(',')
                .append(s.aspirationRetries()).append(',').append(s.evaluationCacheHits()).append(',').append(s.evaluationCacheMisses()).append(',')
                .append(s.requestedWorkers()).append(',').append(s.effectiveWorkers()).append(',').append(s.stopReason()).append('\n');
          }
          try { Files.writeString(file.toPath(), csv.toString(), StandardCharsets.UTF_8); }
          catch (IOException ignored) { }
        });
  }

  private static String csvValue(Object value) {
    String text = value == null ? "" : String.valueOf(value);
    return text.contains(",") || text.contains("\"") ? "\"" + text.replace("\"", "\"\"") + "\"" : text;
  }

  private void receiveSnapshot(SearchTelemetrySnapshot snapshot) {
    if (telemetryService.sessionStartedAt() != null && !telemetryService.sessionStartedAt().equals(sessionStartedAt)) {
      maxima.clear();
      sessionStartedAt = telemetryService.sessionStartedAt();
    }
    latestSnapshot = snapshot;
    if (uiUpdatePending.compareAndSet(false, true)) {
      Platform.runLater(this::flushSnapshot);
    }
  }

  private void flushSnapshot() {
    if (frequencySelector == null) {
      uiUpdatePending.set(false);
      return;
    }
    long intervalNanos = 1_000_000_000L / Math.max(1, frequencySelector.getValue());
    long elapsed = System.nanoTime() - lastRenderNanos;
    if (lastRenderNanos != 0 && elapsed < intervalNanos) {
      PauseTransition delay = new PauseTransition(
          Duration.millis(Math.max(1.0, (intervalNanos - elapsed) / 1_000_000.0)));
      delay.setOnFinished(event -> flushSnapshot());
      delay.play();
      return;
    }
    SearchTelemetrySnapshot snapshot = latestSnapshot;
    if (snapshot != null) {
      renderSnapshot(snapshot);
      lastRenderNanos = System.nanoTime();
    }
    uiUpdatePending.set(false);
    if (latestSnapshot != snapshot && uiUpdatePending.compareAndSet(false, true)) {
      Platform.runLater(this::flushSnapshot);
    }
  }

  private void renderSnapshot(SearchTelemetrySnapshot snapshot) {
    update("mainNodes", snapshot.mainNodes()); update("qNodes", snapshot.qNodes());
    update("TT hit / cutoff", snapshot.ttHits(), snapshot.ttCutoffs()); update("beta cutoff", snapshot.betaCutoffs());
    update("PVS re-search", snapshot.pvsResearches()); update("null / LMR", snapshot.nullMoveCutoffs(), snapshot.lmrApplications());
    update("aspiration retries", snapshot.aspirationRetries()); update("evaluation cache", snapshot.evaluationCacheHits(), snapshot.evaluationCacheMisses());
    update("workers", snapshot.effectiveWorkers(), snapshot.requestedWorkers()); updateText("stopReason", snapshot.stopReason());
  }

  private void setValue(String metric, Object value) {
    javafx.scene.control.Label label = liveValues.get(metric);
    if (label != null) label.setText(String.valueOf(value));
  }

  private void update(String metric, long... values) {
    StringBuilder current = new StringBuilder(); StringBuilder maximum = new StringBuilder();
    for (int i = 0; i < values.length; i++) { long value = values[i];
      if (current.length() > 0) { current.append(" / "); maximum.append(" / "); }
      current.append(value); String key = metric + "#" + i; long max = Math.max(maxima.getOrDefault(key, 0L), value);
      maxima.put(key, max); maximum.append(max);
    }
    setValue(metric, current); setMaxValue(metric, maximum);
  }
  private void updateText(String metric, Object value) { setValue(metric, value); setMaxValue(metric, value); }
  private void setMaxValue(String metric, Object value) { var label = maxValues.get(metric); if (label != null) label.setText(String.valueOf(value)); }
}
