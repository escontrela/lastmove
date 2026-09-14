package com.escontrela.lastmove.ui.component.tag;

import com.escontrela.lastmove.application.service.TagService;
import com.escontrela.lastmove.application.tag.ManagedTag;
import com.escontrela.lastmove.ui.component.message.MessageBox;
import com.escontrela.lastmove.ui.component.message.MessageBoxButtonMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/** Reusable in-screen editor for the filters shared by every asset library. */
public final class TagAdministrationControl extends MessageBox {
  private final TagService tags;
  private final VBox rows = new VBox();
  private final Label validation = new Label();
  private final List<Row> drafts = new ArrayList<>();
  private Runnable onApplied = () -> {};
  private Runnable onDismissed = () -> {};

  public TagAdministrationControl(TagService tags) {
    this.tags = Objects.requireNonNull(tags, "tags must not be null");
    getStyleClass().add("tag-administration-control");
    setTitle("Manage filters");
    setMessage("Rename a filter or mark it for permanent deletion from every study, tactic suite and game.");
    setAcceptText("Apply changes");
    setCancelText("Cancel");
    setButtonMode(MessageBoxButtonMode.ACCEPT_CANCEL);
    setCardWidth(680);
    setContentPadding(24);
    setContentSpacing(12);
    setDragEnabled(false);
    setAutoHide(false);

    rows.getStyleClass().add("tag-admin-list");
    ScrollPane scroll = new ScrollPane(rows);
    scroll.setFitToWidth(true);
    scroll.setPrefViewportHeight(390);
    scroll.getStyleClass().add("tag-admin-scroll");
    validation.setWrapText(true);
    validation.getStyleClass().addAll("settings-validation-message", "settings-validation-error");
    VBox body = new VBox(10, scroll, validation);
    body.setPadding(new Insets(2, 0, 0, 0));
    setBody(body);
    setOnAccept(event -> apply());
    setOnCancel(event -> dismiss());
    setOnClose(event -> dismiss());
  }

  public static void showIn(Scene scene, TagService tags, Runnable onApplied) {
    Objects.requireNonNull(scene, "scene must not be null");
    Parent originalRoot = scene.getRoot();
    StackPane host;
    boolean wrapped;
    if (originalRoot instanceof StackPane stackPane) {
      host = stackPane;
      wrapped = false;
    } else {
      host = new StackPane(originalRoot);
      host.getStyleClass().add("app-shell");
      scene.setRoot(host);
      wrapped = true;
    }
    TagAdministrationControl control = new TagAdministrationControl(tags);
    boolean restoreRoot = wrapped;
    control.onApplied = Objects.requireNonNull(onApplied, "onApplied must not be null");
    control.onDismissed = () -> {
      host.getChildren().remove(control);
      if (restoreRoot && scene.getRoot() == host) {
        host.getChildren().remove(originalRoot);
        scene.setRoot(originalRoot);
      }
    };
    host.getChildren().add(control);
    control.reload();
    control.show();
  }

  private void reload() {
    drafts.clear();
    rows.getChildren().clear();
    for (ManagedTag tag : tags.managedTags()) {
      Row row = new Row(tag);
      drafts.add(row);
      rows.getChildren().add(row.node());
    }
    if (drafts.isEmpty()) {
      Label empty = new Label("There are no filters to manage yet.");
      empty.getStyleClass().add("tag-admin-empty");
      rows.getChildren().add(empty);
    }
    validation.setText("");
  }

  private void apply() {
    try {
      for (Row row : drafts) row.apply(tags);
      onApplied.run();
      dismiss();
    } catch (RuntimeException failure) {
      validation.setText(readableMessage(failure));
    }
  }

  private void dismiss() {
    hide();
    onDismissed.run();
  }

  private static String readableMessage(RuntimeException failure) {
    String message = failure.getMessage();
    if (message != null && message.toLowerCase(java.util.Locale.ROOT).contains("unique")) {
      return "Another filter already uses that name.";
    }
    return message == null || message.isBlank() ? "The filters could not be updated." : message;
  }

  private static final class Row {
    private final ManagedTag original;
    private final TextField name;
    private final Button delete;
    private final HBox node;
    private boolean deleted;

    private Row(ManagedTag tag) {
      original = tag;
      name = new TextField(tag.name());
      name.getStyleClass().addAll("tag-admin-name", "regex-search-field");
      HBox.setHgrow(name, Priority.ALWAYS);
      Label count = new Label("(" + tag.assetCount() + ")");
      count.setAccessibleText(tag.assetCount() + " assigned assets");
      count.getStyleClass().add("tag-admin-count");
      delete = new Button();
      delete.getStyleClass().addAll("tag-admin-delete", "tactics-page-action-button");
      ImageView trash = new ImageView(new Image(Objects.requireNonNull(
          TagAdministrationControl.class.getResourceAsStream("/images/delete_35dp_000000.png"))));
      trash.setFitWidth(16); trash.setFitHeight(16); trash.setPreserveRatio(true);
      delete.setGraphic(trash);
      delete.setAccessibleText("Delete filter");
      delete.setOnAction(event -> toggleDeleted());
      node = new HBox(10, name, count, delete);
      node.setAlignment(Pos.CENTER_LEFT);
      node.getStyleClass().add("tag-admin-row");
    }

    private HBox node() { return node; }

    private void toggleDeleted() {
      deleted = !deleted;
      name.setDisable(deleted);
      delete.setAccessibleText(deleted ? "Undo delete filter" : "Delete filter");
      node.getStyleClass().remove("tag-admin-row-deleted");
      if (deleted) node.getStyleClass().add("tag-admin-row-deleted");
    }

    private void apply(TagService service) {
      if (deleted) service.delete(original.id());
      else if (!name.getText().trim().equals(original.name())) service.update(original.id(), name.getText());
    }
  }
}
