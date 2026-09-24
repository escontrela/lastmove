package com.escontrela.lastmove.ui.service;

import java.net.URL;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import javafx.scene.media.AudioClip;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Plays short presentation sound effects bundled with the application.
 *
 * <p>All clips are created eagerly by {@link #preload()} while a screen initializes. This avoids
 * decoder and resource lookup work in the mouse-event path, where even a small delay is noticeable.
 */
@Component
public class ChessSoundService {
  private static final Logger log = LoggerFactory.getLogger(ChessSoundService.class);
  private static final int LOOP_FOREVER = AudioClip.INDEFINITE;

  private final Map<ChessSound, AudioClip> clips = new EnumMap<>(ChessSound.class);
  private boolean preloaded;
  private volatile AnalysisMoveDiagnostic analysisMoveDiagnostic;

  public void beginAnalysisMoveDiagnostic(long id, String move) {
    analysisMoveDiagnostic = new AnalysisMoveDiagnostic(id, move, System.nanoTime());
  }

  public void cancelAnalysisMoveDiagnostic(long id) {
    AnalysisMoveDiagnostic diagnostic = analysisMoveDiagnostic;
    if (diagnostic != null && diagnostic.id() == id) analysisMoveDiagnostic = null;
  }

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
    AnalysisMoveDiagnostic diagnostic = analysisMoveDiagnostic;
    if (diagnostic != null) {
      playWithPlaybackDiagnostics(sound, diagnostic);
      analysisMoveDiagnostic = null;
      return;
    }
    clips.get(sound).play();
  }

  /** Uses MediaPlayer for a traced move so its PLAYING transition can be timed. */
  private void playWithPlaybackDiagnostics(ChessSound sound, AnalysisMoveDiagnostic diagnostic) {
    URL resource = Objects.requireNonNull(getClass().getResource(sound.resourcePath()),
        () -> "Missing chess sound resource: " + sound.resourcePath());
    MediaPlayer player = new MediaPlayer(new Media(resource.toExternalForm()));
    player.setOnReady(() -> log.info(
        "analysis_sound_ready id={} move={} sound={} elapsed_ms={}", diagnostic.id(),
        diagnostic.move(), sound, elapsedMillis(diagnostic.startedNanos())));
    player.setOnPlaying(() -> log.info(
        "analysis_sound_playing id={} move={} sound={} elapsed_ms={}", diagnostic.id(),
        diagnostic.move(), sound, elapsedMillis(diagnostic.startedNanos())));
    player.setOnError(() -> log.warn("analysis_sound_error id={} move={} sound={} error={}",
        diagnostic.id(), diagnostic.move(), sound, player.getError()));
    player.setOnEndOfMedia(player::dispose);
    log.info("analysis_sound_play_requested id={} move={} sound={} elapsed_ms={}", diagnostic.id(),
        diagnostic.move(), sound, elapsedMillis(diagnostic.startedNanos()));
    player.play();
  }

  /** Starts an effect that continues until {@link #stop(ChessSound)} is called. */
  public void playLoop(ChessSound sound) {
    playLoop(sound, 1.0);
  }

  /** Starts a looping effect at the supplied volume. */
  public void playLoop(ChessSound sound, double volume) {
    Objects.requireNonNull(sound, "sound must not be null");
    if (volume < 0.0 || volume > 1.0) {
      throw new IllegalArgumentException("volume must be between 0 and 1");
    }
    preload();
    AudioClip clip = clips.get(sound);
    clip.setVolume(volume);
    clip.setCycleCount(LOOP_FOREVER);
    clip.play();
  }

  /** Stops all playback for the supplied effect, including an effect started in a loop. */
  public void stop(ChessSound sound) {
    Objects.requireNonNull(sound, "sound must not be null");
    AudioClip clip = clips.get(sound);
    if (clip != null) {
      clip.stop();
      clip.setCycleCount(1);
    }
  }

  private AudioClip loadClip(ChessSound sound) {
    URL resource =
        Objects.requireNonNull(
            getClass().getResource(sound.resourcePath()),
            () -> "Missing chess sound resource: " + sound.resourcePath());
    return new AudioClip(resource.toExternalForm());
  }

  private static double elapsedMillis(long startedNanos) {
    return (System.nanoTime() - startedNanos) / 1_000_000.0;
  }

  private record AnalysisMoveDiagnostic(long id, String move, long startedNanos) {}
}
