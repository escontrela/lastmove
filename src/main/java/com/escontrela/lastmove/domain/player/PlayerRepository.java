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

    default Optional<Player> findByExternalIdentity(String provider, String accountId) {
        return Optional.empty();
    }

    List<Player> findAll();

    boolean existsByEmail(String email);
}
