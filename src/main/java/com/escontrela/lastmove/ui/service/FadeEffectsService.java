package com.escontrela.lastmove.ui.service;

import javafx.animation.FadeTransition;
import javafx.scene.Node;
import javafx.util.Duration;
import org.springframework.stereotype.Component;

/** Small collection of reusable, non-blocking JavaFX transition effects. */
@Component
public class FadeEffectsService {

    private static final Duration HALF_FADE = Duration.millis(140);

    /** Fades a node out, applies the update, then fades it back in. */
    public void fadeReplace(Node node, Runnable update) {
        if (node == null || update == null) return;
        FadeTransition out = new FadeTransition(HALF_FADE, node);
        out.setFromValue(node.getOpacity());
        out.setToValue(0.0);
        out.setOnFinished(event -> {
            update.run();
            FadeTransition in = new FadeTransition(HALF_FADE, node);
            in.setFromValue(0.0);
            in.setToValue(1.0);
            in.play();
        });
        out.play();
    }
}
