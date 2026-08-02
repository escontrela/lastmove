package com.escontrela.lastmove.ui.event;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Bus for UI-scoped events that do not need to travel through the application layer.
 *
 * <p>For now this delegates to the Spring {@link ApplicationEventPublisher}. A dedicated
 * lightweight bus can be introduced later if needed.
 */
@Component
public class UiEventBus {

    private final ApplicationEventPublisher publisher;

    public UiEventBus(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    /**
     * Publishes a UI event.
     *
     * @param event the event to publish
     */
    public void publish(Object event) {
        publisher.publishEvent(event);
    }
}
