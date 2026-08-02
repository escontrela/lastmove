package com.escontrela.lastmove.application.service;

import com.escontrela.lastmove.application.dto.MoveNavigationResult;
import com.escontrela.lastmove.application.event.CurrentMoveChangedEvent;
import com.escontrela.lastmove.domain.game.Game;
import com.escontrela.lastmove.domain.service.GameNavigationService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * Application service that coordinates move-by-move replay of a loaded game.
 *
 * <p>Delegates cursor movement to {@link GameNavigationService} and publishes
 * {@link CurrentMoveChangedEvent} after each navigation step.
 */
@Service
public class GameReplayService {

    private final GameNavigationService navigationService;
    private final ApplicationEventPublisher eventPublisher;

    public GameReplayService(GameNavigationService navigationService,
                             ApplicationEventPublisher eventPublisher) {
        this.navigationService = navigationService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Advances to the next move in the game.
     *
     * @param game the game being replayed
     * @return navigation result with the new current position
     */
    public MoveNavigationResult next(Game game) {
        // TODO: implement next-move navigation and publish CurrentMoveChangedEvent
        throw new UnsupportedOperationException("GameReplayService.next is not yet implemented");
    }

    /**
     * Steps back to the previous move.
     *
     * @param game the game being replayed
     * @return navigation result with the new current position
     */
    public MoveNavigationResult previous(Game game) {
        // TODO: implement previous-move navigation and publish CurrentMoveChangedEvent
        throw new UnsupportedOperationException("GameReplayService.previous is not yet implemented");
    }
}
