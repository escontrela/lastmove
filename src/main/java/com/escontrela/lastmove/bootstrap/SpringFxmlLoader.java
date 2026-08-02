package com.escontrela.lastmove.bootstrap;

import java.io.IOException;
import java.net.URL;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/** Loads FXML resources while resolving their controllers through Spring. */
@Component
public class SpringFxmlLoader {

    private final ApplicationContext applicationContext;

    public SpringFxmlLoader(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    public Parent load(String location) {
        URL resource = SpringFxmlLoader.class.getResource(location);
        if (resource == null) {
            throw new IllegalArgumentException("FXML resource not found: " + location);
        }
        FXMLLoader loader = new FXMLLoader(resource);
        loader.setControllerFactory(applicationContext::getBean);
        try {
            return loader.load();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load FXML resource: " + location, exception);
        }
    }
}
