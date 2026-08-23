package com.escontrela.lastmove.ui.component.search;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import com.escontrela.lastmove.ui.component.toolbar.ToolbarIconButton;

/**
 * Theme-aware regex input for library/list screens.
 *
 * <p>The control validates and emits a query, but deliberately does not know about the data it
 * filters. Owners retain their source list and decide which row fields are searchable.
 */
public final class RegexSearchControl extends VBox {

  private final TextField queryField = new TextField();
  private final ToolbarIconButton searchButton = new ToolbarIconButton();
  private final Label validationLabel = new Label();
  private final ReadOnlyBooleanWrapper valid = new ReadOnlyBooleanWrapper(this, "valid", true);
  private final StringProperty validationMessage =
      new SimpleStringProperty(this, "validationMessage", "");
  private final ObjectProperty<EventHandler<RegexSearchEvent>> onSearch =
      new SimpleObjectProperty<>(this, "onSearch");

  public RegexSearchControl() {
    getStyleClass().add("regex-search-control");
    setSpacing(5);

    queryField.getStyleClass().add("regex-search-field");
    queryField.setPromptText("Search with a regular expression");
    queryField.setAccessibleText("Regular expression search query");
    queryField.setOnAction(event -> submit());
    queryField.textProperty().addListener((ignored, oldText, newText) -> {
      if (newText.isEmpty()) {
        submit();
      } else {
        validate(newText);
      }
    });

    searchButton.getStyleClass().add("regex-search-button");
    searchButton.setLightIconResource("/images/search_35dp_000000.png");
    searchButton.setDarkIconResource("/images/search_35dp_FFFFFF.png");
    searchButton.setTooltipText("Search");
    searchButton.setAccessibleText("Search with regular expression");
    searchButton.setOnAction(event -> submit());

    validationLabel.getStyleClass().add("regex-search-validation");
    validationLabel.textProperty().bind(validationMessage);
    validationLabel.setWrapText(true);
    validationLabel.setVisible(false);
    validationLabel.setManaged(false);
    validationMessage.addListener(
        (ignored, oldMessage, newMessage) -> {
          boolean visible = !newMessage.isBlank();
          validationLabel.setVisible(visible);
          validationLabel.setManaged(visible);
        });
    onSearch.addListener(
        (ignored, oldHandler, newHandler) -> setEventHandler(RegexSearchEvent.SEARCH, newHandler));

    HBox input = new HBox(8, queryField, searchButton);
    input.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(queryField, Priority.ALWAYS);
    getChildren().addAll(input, validationLabel);
  }

  /** Validates the current text and emits it only when it is valid. */
  public void submit() {
    Optional<Pattern> pattern = validate(queryField.getText());
    pattern.ifPresent(value -> fireEvent(new RegexSearchEvent(this, queryField.getText(), value)));
  }

  public TextField queryField() {
    return queryField;
  }

  public ReadOnlyBooleanProperty validProperty() {
    return valid.getReadOnlyProperty();
  }

  public boolean isValid() {
    return valid.get();
  }

  public StringProperty validationMessageProperty() {
    return validationMessage;
  }

  public String getValidationMessage() {
    return validationMessage.get();
  }

  public ObjectProperty<EventHandler<RegexSearchEvent>> onSearchProperty() {
    return onSearch;
  }

  public void setOnSearch(EventHandler<RegexSearchEvent> handler) {
    onSearch.set(handler);
  }

  public EventHandler<RegexSearchEvent> getOnSearch() {
    return onSearch.get();
  }

  /** Validates a query without changing a list; useful for controllers and focused unit tests. */
  public static Optional<Pattern> compile(String query) {
    try {
      return Optional.of(Pattern.compile(Objects.requireNonNullElse(query, "")));
    } catch (PatternSyntaxException exception) {
      return Optional.empty();
    }
  }

  private Optional<Pattern> validate(String query) {
    try {
      Pattern pattern = Pattern.compile(query);
      valid.set(true);
      validationMessage.set("");
      return Optional.of(pattern);
    } catch (PatternSyntaxException exception) {
      valid.set(false);
      validationMessage.set("Invalid regular expression: " + exception.getDescription());
      return Optional.empty();
    }
  }
}
