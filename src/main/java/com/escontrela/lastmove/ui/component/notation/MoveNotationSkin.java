package com.escontrela.lastmove.ui.component.notation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SkinBase;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Default virtualized skin for {@link MoveNotationControl}. */
public final class MoveNotationSkin extends SkinBase<MoveNotationControl> {

  private final ListView<MoveNotationRow> rowList = new ListView<>();
  private final VBox content = new VBox();
  private final ListChangeListener<MoveNotationNode> rootsListener = change -> rebuildRows();
  private final ChangeListener<UUID> selectionListener =
      (observable, previous, current) -> refreshSelection(current);

  public MoveNotationSkin(MoveNotationControl control) {
    super(control);
    HBox columnHeader = createColumnHeader();
    rowList.getStyleClass().add("move-notation-list");
    rowList.setFocusTraversable(false);
    rowList.setCellFactory(ignored -> new MoveRowCell(control));
    VBox.setVgrow(rowList, Priority.ALWAYS);
    content.getStyleClass().add("move-notation-content");
    content.getChildren().addAll(columnHeader, rowList);
    control.observableRoots().addListener(rootsListener);
    control.selectedNodeIdProperty().addListener(selectionListener);
    rebuildRows();
    getChildren().add(content);
  }

  private static HBox createColumnHeader() {
    Region variationSpacer = new Region();
    variationSpacer.getStyleClass().add("variation-header-spacer");

    Label numberHeader = new Label("#");
    numberHeader.getStyleClass().addAll("move-notation-column-header", "move-number-header");
    numberHeader.setAccessibleText("Move number");

    Label whiteHeader = new Label("White");
    whiteHeader.getStyleClass().addAll("move-notation-column-header", "move-color-header");
    whiteHeader.setAccessibleText("White moves");

    Label blackHeader = new Label("Black");
    blackHeader.getStyleClass().addAll("move-notation-column-header", "move-color-header");
    blackHeader.setAccessibleText("Black moves");

    whiteHeader.setMaxWidth(Double.MAX_VALUE);
    blackHeader.setMaxWidth(Double.MAX_VALUE);
    whiteHeader.setMinWidth(0);
    blackHeader.setMinWidth(0);
    whiteHeader.setPrefWidth(0);
    blackHeader.setPrefWidth(0);
    HBox.setHgrow(whiteHeader, Priority.ALWAYS);
    HBox.setHgrow(blackHeader, Priority.ALWAYS);

    HBox header = new HBox(variationSpacer, numberHeader, whiteHeader, blackHeader);
    header.getStyleClass().add("move-notation-header");
    header.setAlignment(Pos.CENTER_LEFT);
    return header;
  }

  @Override
  public void dispose() {
    getSkinnable().observableRoots().removeListener(rootsListener);
    getSkinnable().selectedNodeIdProperty().removeListener(selectionListener);
    super.dispose();
  }

  private void rebuildRows() {
    rowList.setItems(
        FXCollections.observableArrayList(MoveNotationRow.flatten(getSkinnable().getTree())));
    refreshSelection(getSkinnable().getSelectedNodeId());
  }

  private void refreshSelection(UUID selectedNodeId) {
    rowList.refresh();
    if (selectedNodeId == null) {
      return;
    }
    List<MoveNotationRow> rows = rowList.getItems();
    for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
      MoveNotationRow row = rows.get(rowIndex);
      if (containsNode(row.whiteMove(), selectedNodeId)
          || containsNode(row.blackMove(), selectedNodeId)) {
        rowList.scrollTo(rowIndex);
        return;
      }
    }
  }

  private boolean containsNode(Optional<MoveNotationEntry> move, UUID nodeId) {
    return move.map(entry -> entry.nodeId().equals(nodeId)).orElse(false);
  }

  private static final class MoveRowCell extends ListCell<MoveNotationRow> {

    private static final PseudoClass CURRENT = PseudoClass.getPseudoClass("current");
    private static final PseudoClass CURRENT_ROW = PseudoClass.getPseudoClass("current-row");
    private static final PseudoClass VARIATION = PseudoClass.getPseudoClass("variation");
    private static final PseudoClass ACTIVE_LINE = PseudoClass.getPseudoClass("active-line");

    private final MoveNotationControl control;
    private final HBox row = new HBox();
    private final Label variationMarker = new Label();
    private final Label moveNumber = new Label();
    private final Button whiteMove = new Button();
    private final Button blackMove = new Button();

    private MoveRowCell(MoveNotationControl control) {
      this.control = control;
      getStyleClass().add("move-notation-cell");
      row.getStyleClass().add("move-notation-row");
      row.setAlignment(Pos.CENTER_LEFT);
      row.setMaxWidth(Double.MAX_VALUE);
      row.prefWidthProperty().bind(widthProperty().subtract(2));
      variationMarker.getStyleClass().add("variation-marker");
      moveNumber.getStyleClass().add("move-number");
      whiteMove.getStyleClass().add("notation-move");
      blackMove.getStyleClass().add("notation-move");
      whiteMove.setMaxWidth(Double.MAX_VALUE);
      blackMove.setMaxWidth(Double.MAX_VALUE);
      whiteMove.setMinWidth(0);
      blackMove.setMinWidth(0);
      whiteMove.setPrefWidth(0);
      blackMove.setPrefWidth(0);
      HBox.setHgrow(whiteMove, Priority.ALWAYS);
      HBox.setHgrow(blackMove, Priority.ALWAYS);
      row.getChildren().addAll(variationMarker, moveNumber, whiteMove, blackMove);
    }

    @Override
    protected void updateItem(MoveNotationRow item, boolean empty) {
      super.updateItem(item, empty);
      if (empty || item == null) {
        row.pseudoClassStateChanged(VARIATION, false);
        row.pseudoClassStateChanged(ACTIVE_LINE, false);
        row.pseudoClassStateChanged(CURRENT_ROW, false);
        setGraphic(null);
        return;
      }
      row.setPadding(new Insets(0, 0, 0, Math.min(item.depth(), 4) * 16.0));
      row.pseudoClassStateChanged(VARIATION, item.depth() > 0);
      row.pseudoClassStateChanged(ACTIVE_LINE, containsActiveMove(item));
      row.pseudoClassStateChanged(CURRENT_ROW, containsCurrentMove(item));
      variationMarker.setText(item.variationStart() ? "↳" : "");
      variationMarker.setAccessibleText(item.variationStart() ? "Variation" : "");
      moveNumber.setText(Integer.toString(item.moveNumber()));
      configureMove(whiteMove, item.whiteMove(), "White");
      configureMove(blackMove, item.blackMove(), "Black");
      setGraphic(row);
    }

    private boolean containsActiveMove(MoveNotationRow row) {
      return row.whiteMove().map(MoveNotationEntry::activeLine).orElse(false)
          || row.blackMove().map(MoveNotationEntry::activeLine).orElse(false);
    }

    private boolean containsCurrentMove(MoveNotationRow row) {
      UUID selectedNodeId = control.getSelectedNodeId();
      return selectedNodeId != null
          && (row.whiteMove().map(move -> move.nodeId().equals(selectedNodeId)).orElse(false)
              || row.blackMove().map(move -> move.nodeId().equals(selectedNodeId)).orElse(false));
    }

    private void configureMove(
        Button button, Optional<MoveNotationEntry> entry, String colorName) {
      if (entry.isEmpty()) {
        button.setText("");
        button.setDisable(true);
        button.setMouseTransparent(true);
        button.setOnAction(null);
        button.setOnContextMenuRequested(null);
        button.pseudoClassStateChanged(CURRENT, false);
        if (!button.getStyleClass().contains("empty")) {
          button.getStyleClass().add("empty");
        }
        return;
      }

      MoveNotationEntry move = entry.orElseThrow();
      button.getStyleClass().remove("empty");
      button.setDisable(false);
      button.setMouseTransparent(false);
      button.setText(move.san());
      button.setAccessibleText(
          "Move " + move.moveNumber() + ", " + colorName + ", " + move.san());
      button.pseudoClassStateChanged(
          CURRENT, move.nodeId().equals(control.getSelectedNodeId()));
      button.setOnAction(event -> control.requestSelection(move));
      button.setOnContextMenuRequested(
          event -> {
            control.requestContextMenu(move, event.getSceneX(), event.getSceneY());
            event.consume();
          });
    }
  }
}
