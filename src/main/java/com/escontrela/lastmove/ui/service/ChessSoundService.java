package com.escontrela.lastmove.ui.service;

import java.net.URL;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import javafx.scene.media.AudioClip;
import org.springframework.stereotype.Component;

/**
 * Plays short presentation sound effects bundled with the application.
 *
 * <p>All clips are created eagerly by {@link #preload()} while a screen initializes. This avoids
 * decoder and resource lookup work in the mouse-event path, where even a small delay is noticeable.
 */
@Component
public class ChessSoundService {

  private final Map<ChessSound, AudioClip> clips = new EnumMap<>(ChessSound.class);
  private boolean preloaded;

  /** Preloads every bundled effect. Call this once from JavaFX screen initialization. */
  public synchronized void preload() {
    if (preloaded) {
      return;
    }
    for (ChessSound sound : ChessSound.values()) {
      clips.put(sound, loadClip(sound));
    }
    preloaded = true;
  }

  /** Plays a preloaded effect. Multiple clips may overlap. */
  public void play(ChessSound sound) {
    Objects.requireNonNull(sound, "sound must not be null");
    preload();
    clips.get(sound).play();
  }

  private AudioClip loadClip(ChessSound sound) {
    URL resource =
        Objects.requireNonNull(
            getClass().getResource(sound.resourcePath()),
            () -> "Missing chess sound resource: " + sound.resourcePath());
    return new AudioClip(resource.toExternalForm());
  }
}
