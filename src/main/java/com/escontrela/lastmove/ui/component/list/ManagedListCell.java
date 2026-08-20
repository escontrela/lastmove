package com.escontrela.lastmove.ui.component.list;

import javafx.scene.control.ListCell;
import javafx.scene.input.ContextMenuEvent;

/**
 * List cell that makes the row under a context-menu request the selected row first.
 *
 * <p>This keeps the visual selection and the target of destructive row actions in sync across
 * managed library lists.
 */
public abstract class ManagedListCell<T> extends ListCell<T> {

  protected ManagedListCell() {
    addEventFilter(
        ContextMenuEvent.CONTEXT_MENU_REQUESTED,
        event -> {
          if (!isEmpty() && getItem() != null && getListView() != null) {
            getListView().getSelectionModel().select(getIndex());
            getListView().requestFocus();
          }
        });
  }
}
