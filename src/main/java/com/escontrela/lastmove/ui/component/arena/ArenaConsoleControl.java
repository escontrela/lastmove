package com.escontrela.lastmove.ui.component.arena;

import java.util.List;
import java.util.Objects;
import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.VBox;

/** Scrollable terminal-style output panel whose rows can act as lightweight commands. */
public final class ArenaConsoleControl extends VBox {
  public record Entry(String text, Runnable onActivate) {
    public Entry { text = Objects.requireNonNullElse(text, "").trim(); }
  }

  private final VBox output = new VBox();
  private final Label empty = new Label();

  public ArenaConsoleControl() {
    getStyleClass().add("arena-console");
    output.getStyleClass().add("arena-console-output");
    empty.getStyleClass().add("arena-console-empty");
    ScrollPane scroll = new ScrollPane(output);
    scroll.setFitToWidth(true);
    scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
    scroll.getStyleClass().add("arena-console-scroll");
    VBox.setVgrow(scroll, javafx.scene.layout.Priority.ALWAYS);
    getChildren().addAll(scroll, empty);
  }

  public void setEntries(List<Entry> entries, String emptyText) {
    List<Entry> values = entries == null ? List.of() : entries;
    output.getChildren().setAll(values.stream().filter(entry -> !entry.text().isBlank()).map(this::row).toList());
    boolean hasEntries = !values.isEmpty();
    empty.setText(Objects.requireNonNullElse(emptyText, "No output."));
    empty.setVisible(!hasEntries);
    empty.setManaged(!hasEntries);
  }

  private Label row(Entry entry) {
    Label line = new Label("$ " + entry.text());
    line.setWrapText(true);
    line.setMaxWidth(Double.MAX_VALUE);
    line.getStyleClass().add("arena-console-line");
    if (entry.onActivate() != null) {
      line.getStyleClass().add("arena-console-action");
      line.setCursor(Cursor.HAND);
      line.setOnMouseClicked(event -> { if (event.getButton() == MouseButton.PRIMARY) entry.onActivate().run(); });
    }
    VBox.setMargin(line, new Insets(0));
    return line;
  }
}
