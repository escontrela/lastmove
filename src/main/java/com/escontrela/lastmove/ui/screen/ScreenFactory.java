package com.escontrela.lastmove.ui.screen;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URL;

/**
 * Factory that creates JavaFX screens by loading FXML files and injecting
 * Spring-managed controllers via the application context.
 *
 * <p>Placeholder – screen factory will be expanded once multi-screen navigation is needed.
 */
@Component
public class ScreenFactory {

    private final ApplicationContext context;

    public ScreenFactory(ApplicationContext context) {
        this.context = context;
    }

    /**
     * Loads an FXML resource and wires its controller from the Spring context.
     *
     * @param fxmlPath classpath-relative path to the FXML file, e.g. {@code "/fxml/main-screen.fxml"}
     * @return the loaded scene root
     * @throws IOException if the FXML cannot be loaded
     */
    public Parent load(String fxmlPath) throws IOException {
        URL url = getClass().getResource(fxmlPath);
        if (url == null) {
            throw new IllegalArgumentException("FXML not found on classpath: " + fxmlPath);
        }
        FXMLLoader loader = new FXMLLoader(url);
        loader.setControllerFactory(context::getBean);
        return loader.load();
    }
}
