package com.escontrela.lastmove.ui.component.settings;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Priority;
import javafx.scene.shape.Rectangle;

/** Reusable, searchable navigation control for the Settings groups. */
public final class SettingsNavigationControl extends VBox {
    private record Entry(String key, String label) {}

    private final TextField searchField = new TextField();
    private final Label title = new Label("Settings");
    private final VBox itemContainer = new VBox(4);
    private final ScrollPane itemsScrollPane = new ScrollPane(itemContainer);
    private final List<Entry> entries = new ArrayList<>();
    private final StringProperty selectedKey = new SimpleStringProperty(this, "selectedKey", "appearance");
    private Consumer<String> onItemSelected = ignored -> {};

    public SettingsNavigationControl() {
        getStyleClass().add("settings-navigation");
        setSpacing(4);
        searchField.setPromptText("Search");
        searchField.setFocusTraversable(false);
        searchField.getStyleClass().add("settings-navigation-search");
        itemContainer.getStyleClass().add("settings-navigation-items");
        itemsScrollPane.setFitToWidth(true);
        itemsScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        itemsScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        itemsScrollPane.getStyleClass().add("settings-navigation-scroll");
        VBox.setVgrow(itemsScrollPane, Priority.ALWAYS);
        title.getStyleClass().add("settings-navigation-title");
        getChildren().addAll(title, searchField, itemsScrollPane);
        searchField.textProperty().addListener((obs, oldValue, newValue) -> rebuildItems());
        selectedKey.addListener((obs, oldValue, newValue) -> rebuildItems());

        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(widthProperty());
        clip.heightProperty().bind(heightProperty());
        clip.setArcWidth(28);
        clip.setArcHeight(28);
        setClip(clip);
    }

    public void addItem(String key, String label) {
        entries.add(new Entry(key, label));
        rebuildItems();
    }

    public StringProperty selectedKeyProperty() { return selectedKey; }
    public void setSelectedKey(String key) { selectedKey.set(key); }
    public String getSelectedKey() { return selectedKey.get(); }
    public void setOnItemSelected(Consumer<String> callback) { onItemSelected = callback == null ? ignored -> {} : callback; }

    private void rebuildItems() {
        itemContainer.getChildren().clear();
        String query = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase();
        for (Entry entry : entries) {
            if (!query.isEmpty() && !entry.label().toLowerCase().contains(query)) continue;
            Button item = new Button(entry.label());
            item.setMnemonicParsing(false);
            item.setMaxWidth(Double.MAX_VALUE);
            item.setAlignment(Pos.CENTER_LEFT);
            item.getStyleClass().add("settings-navigation-item");
            if (entry.key().equals(getSelectedKey())) item.getStyleClass().add("settings-navigation-item-active");
            item.setOnAction(event -> {
                setSelectedKey(entry.key());
                onItemSelected.accept(entry.key());
            });
            itemContainer.getChildren().add(item);
        }
    }
}
