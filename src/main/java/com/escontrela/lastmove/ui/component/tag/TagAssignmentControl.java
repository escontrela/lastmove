package com.escontrela.lastmove.ui.component.tag;

import com.escontrela.lastmove.application.tag.Tag;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Reusable, resource-agnostic tag editor.
 *
 * <p>Owners provide currently assigned and available tags, then persist the two emitted actions.
 * This keeps the component suitable for games, studies and tactic suites.
 */
public final class TagAssignmentControl extends VBox {
  private final TextField entry = new TextField();
  private final Button addButton = new Button("+");
  private final FlowPane assigned = new FlowPane(6, 6);
  private final FlowPane matches = new FlowPane(6, 6);
  private List<Tag> availableTags = List.of();
  private List<Tag> assignedTags = List.of();
  private Consumer<String> onAssign = ignored -> {};
  private Consumer<Tag> onRemove = ignored -> {};

  public TagAssignmentControl() {
    getStyleClass().add("tag-assignment-control");
    setSpacing(6);
    entry.getStyleClass().add("tag-entry-field");
    entry.setPromptText("Add or create a tag");
    entry.setAccessibleText("Tag name");
    entry.setOnAction(event -> assignEntry());
    entry.textProperty().addListener((ignored, previous, value) -> renderMatches(value));
    addButton.getStyleClass().add("tag-add-button");
    addButton.setAccessibleText("Add tag");
    addButton.setTooltip(new Tooltip("Add tag"));
    addButton.setOnAction(event -> assignEntry());
    HBox input = new HBox(6, entry, addButton);
    input.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(entry, Priority.ALWAYS);
    assigned.getStyleClass().add("tag-chip-flow");
    matches.getStyleClass().add("tag-match-flow");
    getChildren().addAll(input, matches, assigned);
  }

  public void setAvailableTags(List<Tag> tags) {
    availableTags = List.copyOf(Objects.requireNonNull(tags, "tags must not be null"));
    String suggestions = availableTags.stream().map(Tag::name).limit(12).reduce((left, right) -> left + ", " + right).orElse("");
    entry.setPromptText(suggestions.isBlank() ? "Add or create a tag" : "Add tag (e.g. " + suggestions + ")");
    renderMatches(entry.getText());
  }

  public void setAssignedTags(List<Tag> tags) {
    assignedTags = List.copyOf(Objects.requireNonNull(tags, "tags must not be null"));
    assigned.getChildren().setAll(assignedTags.stream().map(this::chip).toList());
    renderMatches(entry.getText());
  }

  public void setOnAssign(Consumer<String> handler) {
    onAssign = Objects.requireNonNull(handler, "handler must not be null");
  }

  public void setOnRemove(Consumer<Tag> handler) {
    onRemove = Objects.requireNonNull(handler, "handler must not be null");
  }

  private Button chip(Tag tag) {
    Button button = new Button(tag.name() + "  ×");
    button.getStyleClass().add("tag-chip");
    button.setAccessibleText("Remove tag " + tag.name());
    button.setOnAction(event -> onRemove.accept(tag));
    return button;
  }

  private void assignEntry() {
    String name = entry.getText().trim();
    if (name.isEmpty()) return;
    onAssign.accept(name);
    entry.clear();
  }

  private void renderMatches(String query) {
    String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
    if (normalized.isEmpty()) {
      matches.getChildren().clear();
      matches.setVisible(false);
      matches.setManaged(false);
      return;
    }
    matches.getChildren().setAll(availableTags.stream()
        .filter(tag -> tag.name().toLowerCase(Locale.ROOT).contains(normalized))
        .filter(tag -> assignedTags.stream().noneMatch(assignedTag -> assignedTag.id() == tag.id()))
        .limit(8).map(this::match).toList());
    boolean visible = !matches.getChildren().isEmpty();
    matches.setVisible(visible);
    matches.setManaged(visible);
  }

  private Button match(Tag tag) {
    Button button = new Button(tag.name());
    button.getStyleClass().add("tag-match");
    button.setAccessibleText("Use existing tag " + tag.name());
    button.setOnAction(event -> {
      onAssign.accept(tag.name());
      entry.clear();
    });
    return button;
  }
}
