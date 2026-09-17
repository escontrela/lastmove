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
import com.escontrela.lastmove.ui.component.statistics.GameStatisticsChartControl;
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
  private final Map<String, javafx.scene.control.Label> avgValues = new HashMap<>();
  private final Map<String, String> descriptions = Map.of(
      "mainNodes", "Nodos principales explorados.", "qNodes", "Nodos de búsqueda quiescente.",
      "TT hit / cutoff", "Aciertos y cortes de la tabla de transposición.", "beta cutoff", "Podas por límite beta.",
      "PVS re-search", "Re-búsquedas PVS con ventana completa.", "null / LMR", "Intentos null-move y reducciones LMR.",
      "aspiration retries", "Reintentos al ampliar la ventana de aspiración.", "evaluation cache", "Aciertos y fallos de caché de evaluación.",
      "workers", "Trabajadores solicitados y efectivos.", "stopReason", "Motivo por el que terminó la búsqueda.");
  private final Map<String, double[]> averages = new HashMap<>();
  private final Map<String, VBox> metricCards = new HashMap<>();
  private final Map<String, Long> maxima = new HashMap<>();
  private final Map<String, BooleanProperty> parameterSelection = new HashMap<>();
  private final AtomicBoolean uiUpdatePending = new AtomicBoolean();
  private volatile SearchTelemetrySnapshot latestSnapshot;
  private long lastRenderNanos;
  private GameStatisticsChartControl mainNodesChart;
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
    Scene scene = new Scene(root, 840, 820);
    if (latestSnapshot != null) renderSnapshot(latestSnapshot);
    String stylesheet = getClass().getResource("/css/lastmove.css").toExternalForm();
    scene.getStylesheets().add(stylesheet);
    stage.setScene(scene);
    stage.setX(primaryStage.getX() + Math.max(20, primaryStage.getWidth() - 860));
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
    javafx.scene.control.Button metricsToggle = new javafx.scene.control.Button();
    metricsToggle.getStyleClass().addAll("message-box-button", "message-box-additional-button");
    metricsToggle.getStyleClass().add("blood-pressure-metrics-toggle");
    metricsToggle.setOnAction(event -> active.set(!active.get()));
    Runnable updateToggleLabel = () -> {
      metricsToggle.setText(active.get() ? "Metrics ON" : "Metrics OFF");
      if (active.get()) metricsToggle.getStyleClass().remove("blood-pressure-metrics-off");
      else if (!metricsToggle.getStyleClass().contains("blood-pressure-metrics-off")) metricsToggle.getStyleClass().add("blood-pressure-metrics-off");
    };
    updateToggleLabel.run();
    active.addListener((ignored, oldValue, newValue) -> updateToggleLabel.run());
    GridPane liveMetrics = new GridPane();
    liveMetrics.setHgap(18);
    liveMetrics.setVgap(7);
    for (String metric : METRICS) {
      parameterSelection.put(metric, new SimpleBooleanProperty(telemetryService.visibleMetrics().contains(metric)));
      Label value = new Label("—");
      Label maximum = new Label("—");
      Label average = new Label("—");
      liveValues.put(metric, value);
      maxValues.put(metric, maximum);
      avgValues.put(metric, average);
      Label heading = new Label(metric); heading.getStyleClass().add("blood-pressure-metric-title");
      Label description = new Label(descriptions.get(metric)); description.getStyleClass().add("blood-pressure-metric-description");
      value.getStyleClass().add("blood-pressure-metric-current");
      Label avgCaption = new Label("AVG"); avgCaption.getStyleClass().add("blood-pressure-metric-caption");
      average.getStyleClass().add("blood-pressure-metric-summary-value");
      Label maxCaption = new Label("MAX"); maxCaption.getStyleClass().add("blood-pressure-metric-caption");
      maximum.getStyleClass().add("blood-pressure-metric-summary-value");
      HBox summary = new HBox(6, avgCaption, average, new javafx.scene.layout.Region(), maxCaption, maximum);
      javafx.scene.layout.HBox.setHgrow(summary.getChildren().get(2), javafx.scene.layout.Priority.ALWAYS);
      VBox card = new VBox(3, heading, description, value, summary);
      javafx.scene.control.Tooltip.install(card, new javafx.scene.control.Tooltip(descriptions.get(metric)));
      card.getStyleClass().add("blood-pressure-metric-card");
      metricCards.put(metric, card);
      liveMetrics.add(card, liveMetrics.getChildren().size() % 3, liveMetrics.getChildren().size() / 3);
      boolean visible = telemetryService.visibleMetrics().contains(metric);
      card.setVisible(visible); card.setManaged(visible);
    }
    mainNodesChart = new GameStatisticsChartControl();
    mainNodesChart.setPrefHeight(230);
    mainNodesChart.renderTelemetry(List.of(), "Main nodes");
    Label liveTitle = new Label("Live metrics");
    liveTitle.getStyleClass().add("card-title");
    Label liveHint = new Label("Values appear when Knightshade is thinking.");
    liveHint.getStyleClass().add("hero-support");
    javafx.scene.control.Button export = new javafx.scene.control.Button("Export CSV");
    export.getStyleClass().addAll("message-box-button", "message-box-accept-button");
    export.setOnAction(event -> exportCsv());
    export.disableProperty().bind(active.not());
    HBox actions = new HBox(10, metricsToggle, export);
    actions.setAlignment(Pos.CENTER_LEFT);
    root.getChildren().addAll(title, subtitle, actions, new Separator(), liveTitle, liveHint, mainNodesChart, liveMetrics);
    return root;
  }

  private void exportCsv() {
    var samples = telemetryService.samples();
    if (samples.isEmpty() || monitorStage == null) return;
    fileChooserFactory.chooseCsvExportFile(monitorStage, "knightshade-telemetry")
        .ifPresent(file -> {
          StringBuilder csv = new StringBuilder("sessionStartedAt,timestamp,depth,score,bestMove,elapsedMillis,parameters,mainNodes,qNodes,ttProbes,ttHits,ttCutoffs,betaCutoffs,pvsResearches,nullMoveAttempts,nullMoveCutoffs,lmrApplications,lmrResearches,aspirationRetries,evaluationCacheHits,evaluationCacheMisses,requestedWorkers,effectiveWorkers,stopReason\n");
          String parameters = String.join("|", telemetryService.visibleMetrics());
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
      averages.clear();
      sessionStartedAt = telemetryService.sessionStartedAt();
    }
    latestSnapshot = snapshot;
    if (uiUpdatePending.compareAndSet(false, true)) {
      Platform.runLater(this::flushSnapshot);
    }
  }

  private void flushSnapshot() {
    if (mainNodesChart == null) {
      uiUpdatePending.set(false);
      return;
    }
    long intervalNanos = 1_000_000_000L / Math.max(1, telemetryService.refreshFrequency());
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
      mainNodesChart.renderTelemetry(telemetryService.samples().stream().map(SearchTelemetrySnapshot::mainNodes).toList(), "Main nodes");
      lastRenderNanos = System.nanoTime();
    }
    uiUpdatePending.set(false);
    if (latestSnapshot != snapshot && uiUpdatePending.compareAndSet(false, true)) {
      Platform.runLater(this::flushSnapshot);
    }
  }

  private void renderSnapshot(SearchTelemetrySnapshot snapshot) {
    for (String metric : METRICS) {
      VBox card = metricCards.get(metric);
      if (card != null) { boolean visible = telemetryService.visibleMetrics().contains(metric); card.setVisible(visible); card.setManaged(visible); }
    }
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
    double[] sum = averages.computeIfAbsent(metric, k -> new double[values.length + 1]); sum[0]++;
    for (int i = 0; i < values.length; i++) { long value = values[i]; sum[i + 1] += value;
      if (current.length() > 0) { current.append(" / "); maximum.append(" / "); }
      current.append(value); String key = metric + "#" + i; long max = Math.max(maxima.getOrDefault(key, 0L), value);
      maxima.put(key, max); maximum.append(max);
    }
    StringBuilder average = new StringBuilder();
    for (int i = 0; i < values.length; i++) { if (i > 0) average.append(" / "); average.append(Math.round(sum[i + 1] / sum[0])); }
    setValue(metric, current); setAverageValue(metric, average); setMaxValue(metric, maximum);
  }
  private void updateText(String metric, Object value) { setValue(metric, value); setMaxValue(metric, value); }
  private void setMaxValue(String metric, Object value) { var label = maxValues.get(metric); if (label != null) label.setText(String.valueOf(value)); }
  private void setAverageValue(String metric, Object value) { var label = avgValues.get(metric); if (label != null) label.setText(String.valueOf(value)); }
}
