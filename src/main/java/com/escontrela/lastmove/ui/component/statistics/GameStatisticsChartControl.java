package com.escontrela.lastmove.ui.component.statistics;

import com.escontrela.lastmove.domain.statistics.GameStatisticsBucket;
import java.time.format.DateTimeFormatter;
import java.util.List;
import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.collections.ListChangeListener;
import javafx.scene.Parent;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.util.Duration;

/** Canvas-based line chart for the game's time-series aggregate. */
public final class GameStatisticsChartControl extends Region {
  public enum DisplayMode { GAME_TREND, OUTCOME_BARS }
  private static final double LEFT = 42, RIGHT = 18, TOP = 18, BOTTOM = 34;
  private final Canvas canvas = new Canvas();
  private final FadeTransition loadingPulse = new FadeTransition(Duration.millis(720), canvas);
  private List<GameStatisticsBucket> buckets = List.of();
  private DateTimeFormatter labels = DateTimeFormatter.ofPattern("d MMM");
  private Parent observedRoot;
  private final ListChangeListener<String> themeListener = change -> draw();
  private boolean loading;
  private DisplayMode displayMode = DisplayMode.GAME_TREND;

  public GameStatisticsChartControl() {
    getStyleClass().add("game-statistics-chart");
    getChildren().add(canvas);
    loadingPulse.setFromValue(.32); loadingPulse.setToValue(1); loadingPulse.setAutoReverse(true); loadingPulse.setCycleCount(Animation.INDEFINITE);
    widthProperty().addListener((ignored, oldValue, newValue) -> layoutCanvas());
    heightProperty().addListener((ignored, oldValue, newValue) -> layoutCanvas());
    sceneProperty().addListener((ignored, oldValue, scene) -> observeTheme(scene == null ? null : scene.getRoot()));
  }
  public void render(List<GameStatisticsBucket> value, DateTimeFormatter formatter) {
    buckets = List.copyOf(value); labels = formatter; setLoading(false); draw();
  }
  public void setDisplayMode(DisplayMode value) { displayMode = java.util.Objects.requireNonNull(value, "value"); draw(); }
  public void setLoading(boolean value) {
    loading = value;
    if (loading) loadingPulse.play(); else { loadingPulse.stop(); canvas.setOpacity(1); }
    draw();
  }
  @Override protected void layoutChildren() { layoutCanvas(); }
  @Override protected double computePrefHeight(double width) { return 290; }
  private void layoutCanvas() { canvas.setWidth(getWidth()); canvas.setHeight(getHeight()); draw(); }
  private void observeTheme(Parent root) {
    if (observedRoot != null) observedRoot.getStyleClass().removeListener(themeListener);
    observedRoot = root;
    if (root != null) root.getStyleClass().addListener(themeListener);
    draw();
  }
  private void draw() {
    double width = canvas.getWidth(), height = canvas.getHeight(); if (width <= 0 || height <= 0) return;
    boolean night = observedRoot != null && observedRoot.getStyleClass().contains("night-mode");
    Color grid = Color.web(night ? "#465465" : "#dfe5ec"), text = Color.web(night ? "#b8c5d3" : "#65717e"), line = Color.web(night ? "#68a9ff" : "#1c69d4");
    GraphicsContext g = canvas.getGraphicsContext2D(); g.clearRect(0, 0, width, height);
    double plotWidth = Math.max(1, width - LEFT - RIGHT), plotHeight = Math.max(1, height - TOP - BOTTOM);
    g.setStroke(grid); g.setLineWidth(1); g.setFill(text); g.setFont(javafx.scene.text.Font.font(11));
    long max = displayMode == DisplayMode.OUTCOME_BARS
        ? Math.max(1, buckets.stream().flatMapToLong(bucket -> java.util.stream.LongStream.of(bucket.results().won(), bucket.results().lost())).max().orElse(0))
        : Math.max(1, buckets.stream().mapToLong(GameStatisticsBucket::games).max().orElse(0));
    for (int tick = 0; tick <= 4; tick++) { double y = TOP + plotHeight * tick / 4; g.strokeLine(LEFT, y, width - RIGHT, y); g.fillText(Long.toString(Math.round(max * (4 - tick) / 4.0)), 4, y + 4); }
    if (loading) { g.fillText("Loading game history…", LEFT, TOP + 20); return; }
    if (buckets.isEmpty()) { g.fillText("No finished games match these filters.", LEFT, TOP + plotHeight / 2); return; }
    if (displayMode == DisplayMode.OUTCOME_BARS) { drawOutcomeBars(g, width, height, plotWidth, plotHeight, max, night); return; }
    double step = buckets.size() == 1 ? 0 : plotWidth / (buckets.size() - 1);
    g.setStroke(line); g.setLineWidth(2.5); g.beginPath();
    for (int i = 0; i < buckets.size(); i++) { double x = LEFT + step * i; double y = TOP + plotHeight - (buckets.get(i).games() / (double) max) * plotHeight; if (i == 0) g.moveTo(x, y); else g.lineTo(x, y); }
    g.stroke(); g.setFill(line);
    for (int i = 0; i < buckets.size(); i++) { double x = LEFT + step * i; double y = TOP + plotHeight - (buckets.get(i).games() / (double) max) * plotHeight; g.fillOval(x - 3, y - 3, 6, 6); if (i == 0 || i == buckets.size() - 1 || buckets.size() <= 5) g.fillText(labels.format(buckets.get(i).start()), Math.max(LEFT, x - 18), height - 10); }
  }
  private void drawOutcomeBars(GraphicsContext g, double width, double height, double plotWidth, double plotHeight, long max, boolean night) {
    double group = plotWidth / buckets.size();
    // Give short selected periods visual weight while retaining a readable chart for long ranges.
    double bar = Math.max(3, Math.min(72, group * .34));
    Color won = Color.web(night ? "#4bd38a" : "#1b8f5a"), lost = Color.web(night ? "#ff8794" : "#cc4a55");
    for (int i = 0; i < buckets.size(); i++) {
      var bucket = buckets.get(i); double center = LEFT + group * i + group / 2;
      double wonHeight = plotHeight * bucket.results().won() / max, lostHeight = plotHeight * bucket.results().lost() / max;
      g.setFill(won); g.fillRoundRect(center - bar - 2, TOP + plotHeight - wonHeight, bar, wonHeight, 3, 3);
      g.setFill(lost); g.fillRoundRect(center + 2, TOP + plotHeight - lostHeight, bar, lostHeight, 3, 3);
      if (i == 0 || i == buckets.size() - 1 || buckets.size() <= 5) { g.setFill(Color.web(night ? "#b8c5d3" : "#65717e")); g.fillText(labels.format(bucket.start()), Math.max(LEFT, center - 20), height - 10); }
    }
    g.setFill(won); g.fillRect(width - 145, 8, 9, 9); g.setFill(Color.web(night ? "#b8c5d3" : "#65717e")); g.fillText("Won", width - 132, 17);
    g.setFill(lost); g.fillRect(width - 82, 8, 9, 9); g.setFill(Color.web(night ? "#b8c5d3" : "#65717e")); g.fillText("Lost", width - 69, 17);
  }
}
