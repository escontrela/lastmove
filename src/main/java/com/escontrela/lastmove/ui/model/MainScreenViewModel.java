package com.escontrela.lastmove.ui.model;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import org.springframework.stereotype.Component;

/**
 * Presentation model for the main application screen.
 *
 * <p>Aggregates the state visible in the top-level layout: title, status message,
 * and whether navigation controls are enabled.
 */
@Component
public class MainScreenViewModel {

    private final StringProperty title = new SimpleStringProperty("LastMove");
    private final StringProperty statusMessage = new SimpleStringProperty("");
    private final BooleanProperty navigationEnabled = new SimpleBooleanProperty(false);

    public StringProperty titleProperty() {
        return title;
    }

    public String getTitle() {
        return title.get();
    }

    public void setTitle(String title) {
        this.title.set(title);
    }

    public StringProperty statusMessageProperty() {
        return statusMessage;
    }

    public String getStatusMessage() {
        return statusMessage.get();
    }

    public void setStatusMessage(String message) {
        statusMessage.set(message);
    }

    public BooleanProperty navigationEnabledProperty() {
        return navigationEnabled;
    }

    public boolean isNavigationEnabled() {
        return navigationEnabled.get();
    }

    public void setNavigationEnabled(boolean enabled) {
        navigationEnabled.set(enabled);
    }
}
