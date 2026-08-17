package com.escontrela.lastmove.domain.player;

import java.util.List;
import java.util.Optional;

/** Domain port for player-profile persistence. */
public interface PlayerRepository {

    Player save(Player player);

    Player update(Player player);

    void deleteById(PlayerId id);

    Optional<Player> findById(PlayerId id);

    Optional<Player> findByEmail(String email);

    List<Player> findAll();

    boolean existsByEmail(String email);
}
