package com.escontrela.lastmove.ui.component.tag;

import com.escontrela.lastmove.application.tag.Tag;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Resource-agnostic multi-tag filter. Selected labels use all-of matching in its owner. */
public final class TagFilterControl extends VBox {
  private final FlowPane choices = new FlowPane(6, 6);
  private final Set<Long> selectedIds = new LinkedHashSet<>();
  private Consumer<Set<Long>> onSelectionChanged = ignored -> {};
  private List<Tag> availableTags = List.of();
  private final Button clear = new Button("Clear");
  private final Button manage = new Button("Admin");
  private Runnable onManage = () -> {};

  public TagFilterControl() {
    getStyleClass().add("tag-filter-control");
    setSpacing(6);
    Label title = new Label("Filter by tags");
    title.getStyleClass().add("tag-filter-title");
    clear.getStyleClass().add("tag-filter-clear");
    clear.setOnAction(event -> { selectedIds.clear(); render(); notifySelection(); });
    Region space = new Region();
    HBox.setHgrow(space, javafx.scene.layout.Priority.ALWAYS);
    manage.getStyleClass().add("tag-filter-manage");
    manage.setOnAction(event -> onManage.run());
    HBox header = new HBox(8, title, space, manage, clear);
    header.setAlignment(Pos.CENTER_LEFT);
    choices.getStyleClass().add("tag-filter-choices");
    getChildren().addAll(header, choices);
    manage.setVisible(false);
    manage.setManaged(false);
    setVisible(false);
    setManaged(false);
  }

  public void setAvailableTags(List<Tag> tags) {
    availableTags = List.copyOf(Objects.requireNonNull(tags, "tags must not be null"));
    selectedIds.retainAll(availableTags.stream().map(Tag::id).collect(java.util.stream.Collectors.toSet()));
    clear.setVisible(!availableTags.isEmpty());
    clear.setManaged(!availableTags.isEmpty());
    boolean shown = !availableTags.isEmpty() || manage.isVisible();
    setVisible(shown);
    setManaged(shown);
    render();
  }

  public Set<Long> selectedTagIds() {
    return Set.copyOf(selectedIds);
  }

  public void setOnSelectionChanged(Consumer<Set<Long>> handler) {
    onSelectionChanged = Objects.requireNonNull(handler, "handler must not be null");
  }

  public void setOnManage(Runnable handler) {
    onManage = Objects.requireNonNull(handler, "handler must not be null");
    manage.setVisible(true);
    manage.setManaged(true);
    setVisible(true);
    setManaged(true);
  }

  private void render() {
    choices.getChildren().setAll(availableTags.stream().map(this::choice).toList());
  }

  private Button choice(Tag tag) {
    Button button = new Button(tag.name());
    button.getStyleClass().add("tag-filter-choice");
    button.getStyleClass().add("tag-tone-" + Math.floorMod(tag.name().toLowerCase(java.util.Locale.ROOT).hashCode(), 6));
    if (selectedIds.contains(tag.id())) button.getStyleClass().add("tag-filter-choice-selected");
    button.setOnAction(event -> {
      if (!selectedIds.add(tag.id())) selectedIds.remove(tag.id());
      render();
      notifySelection();
    });
    return button;
  }

  private void notifySelection() {
    onSelectionChanged.accept(selectedTagIds());
  }
}
