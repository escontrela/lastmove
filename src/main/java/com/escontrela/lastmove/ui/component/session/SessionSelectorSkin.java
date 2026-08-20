package com.escontrela.lastmove.ui.component.session;

import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SkinBase;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

/** Default virtualized skin for {@link SessionSelectorControl}. */
public final class SessionSelectorSkin extends SkinBase<SessionSelectorControl> {

  private final ListView<SessionSelectorEntry> sessionList = new ListView<>();
  private final ListChangeListener<SessionSelectorEntry> entriesListener = change -> rebuild();
  private final ChangeListener<Number> selectionListener =
      (observable, previous, current) -> refreshSelection(current.intValue());

  public SessionSelectorSkin(SessionSelectorControl control) {
    super(control);
    sessionList.getStyleClass().add("session-selector-list");
    sessionList.setFocusTraversable(false);
    sessionList.setCellFactory(ignored -> new SessionCell(control));
    control.observableEntries().addListener(entriesListener);
    control.selectedSessionIndexProperty().addListener(selectionListener);
    rebuild();
    getChildren().add(sessionList);
  }

  @Override
  public void dispose() {
    getSkinnable().observableEntries().removeListener(entriesListener);
    getSkinnable().selectedSessionIndexProperty().removeListener(selectionListener);
    super.dispose();
  }

  private void rebuild() {
    sessionList.setItems(FXCollections.observableArrayList(getSkinnable().getEntries()));
    refreshSelection(getSkinnable().getSelectedSessionIndex());
  }

  private void refreshSelection(int selectedSessionIndex) {
    sessionList.refresh();
    if (selectedSessionIndex >= 0) {
      sessionList.scrollTo(selectedSessionIndex);
    }
  }

  private static final class SessionCell extends ListCell<SessionSelectorEntry> {

    private static final PseudoClass CURRENT = PseudoClass.getPseudoClass("current");

    private final SessionSelectorControl control;
    private final Button rowButton = new Button();
    private final HBox content = new HBox(10);
    private final Label marker = new Label("✓");
    private final Label title = new Label();

    private SessionCell(SessionSelectorControl control) {
      this.control = control;
      getStyleClass().add("session-selector-cell");
      getStyleClass().add("study-library-cell");
      marker.getStyleClass().add("session-active-marker");
      title.getStyleClass().add("session-title");
      title.setMaxWidth(Double.MAX_VALUE);
      HBox.setHgrow(title, Priority.ALWAYS);
      content.setAlignment(Pos.CENTER_LEFT);
      content.getChildren().setAll(marker, title);
      rowButton.getStyleClass().add("session-selector-row");
      rowButton.getStyleClass().add("study-library-row");
      rowButton.setGraphic(content);
      rowButton.setMaxWidth(Double.MAX_VALUE);
      rowButton.prefWidthProperty().bind(widthProperty().subtract(2));
      rowButton.setMnemonicParsing(false);
      rowButton.setOnAction(event -> {
        SessionSelectorEntry entry = getItem();
        if (entry != null) {
          control.requestSelection(entry);
        }
      });
      rowButton.setOnContextMenuRequested(
          event -> {
            SessionSelectorEntry entry = getItem();
            if (entry != null) {
              control.requestContext(entry, event.getSceneX(), event.getSceneY());
            }
            event.consume();
          });
    }

    @Override
    protected void updateItem(SessionSelectorEntry item, boolean empty) {
      super.updateItem(item, empty);
      if (empty || item == null) {
        setGraphic(null);
        return;
      }
      boolean current = item.sessionIndex() == control.getSelectedSessionIndex();
      title.setText(item.title());
      marker.setVisible(current);
      rowButton.pseudoClassStateChanged(CURRENT, current);
      rowButton.setAccessibleText((current ? "Current session, " : "Session, ") + item.title());
      setGraphic(rowButton);
    }
  }
}
