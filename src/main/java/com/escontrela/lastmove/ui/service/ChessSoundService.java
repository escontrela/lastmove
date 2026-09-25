package com.escontrela.lastmove.ui.service;

import java.net.URL;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import jakarta.annotation.PreDestroy;
import javafx.scene.media.AudioClip;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Plays short presentation sound effects bundled with the application.
 *
 * <p>One-shot effects use preloaded PCM clips on a dedicated worker. Long ambient loops retain
 * JavaFX AudioClip and use a separate worker so loop control cannot delay move feedback.
 */
@Component
public class ChessSoundService {
  private static final Logger log = LoggerFactory.getLogger(ChessSoundService.class);
  private static final int LOOP_FOREVER = AudioClip.INDEFINITE;

  private final Map<ChessSound, AudioClip> clips = new EnumMap<>(ChessSound.class);
  private final Map<ChessSound, Clip> oneShotPcmClips = new EnumMap<>(ChessSound.class);
  private final ExecutorService asyncAudioExecutor = Executors.newSingleThreadExecutor(task -> {
    Thread thread = new Thread(task, "chess-audio");
    thread.setDaemon(true);
    return thread;
  });
  private final ExecutorService loopAudioExecutor = Executors.newSingleThreadExecutor(task -> {
    Thread thread = new Thread(task, "chess-audio-loops");
    thread.setDaemon(true);
    return thread;
  });
  private boolean preloaded;

  /** Preloads bundled effects. Call this once from JavaFX screen initialization. */
  public synchronized void preload() {
    if (preloaded) {
      return;
    }
    for (ChessSound sound : ChessSound.values()) {
      clips.put(sound, loadClip(sound));
    }
    preloaded = true;
    asyncAudioExecutor.execute(this::preloadOneShotPcmClips);
  }

  /** Plays a one-shot effect on a dedicated worker using preloaded PCM clips. */
  private void playAsync(ChessSound sound) {
    asyncAudioExecutor.execute(() -> {
      Clip pcmClip = oneShotPcmClips.get(sound);
      if (pcmClip != null) {
        try {
          playPcm(pcmClip);
        } catch (RuntimeException exception) {
          log.warn("pcm_play_failed sound={}; using AudioClip", sound, exception);
          clips.get(sound).play();
        }
      } else {
        clips.get(sound).play();
      }
    });
  }

  private void preloadOneShotPcmClips() {
    for (ChessSound sound : ChessSound.values()) {
      if (isLoopingSound(sound)) continue;
      String pcmResource = sound.resourcePath();
      try (InputStream resource = Objects.requireNonNull(getClass().getResourceAsStream(pcmResource),
              () -> "Missing PCM sound resource: " + pcmResource);
          AudioInputStream audio = AudioSystem.getAudioInputStream(new BufferedInputStream(resource))) {
        Clip clip = AudioSystem.getClip();
        clip.open(audio);
        oneShotPcmClips.put(sound, clip);
      } catch (Exception exception) {
        log.warn("pcm_unavailable sound={} reason={}; using AudioClip", sound,
            exception.toString());
      }
    }
  }

  private static boolean isLoopingSound(ChessSound sound) {
    return switch (sound) {
      case MEMORY_CLOCK_URGENT, MEMORY_BACKGROUND, MEMORY_BACKGROUND_WIND,
          MEMORY_BACKGROUND_RAIN -> true;
      default -> false;
    };
  }

  private void playPcm(Clip clip) {
    clip.stop();
    clip.setFramePosition(0);
    clip.start();
  }

  /** Plays a one-shot effect without waiting for JavaFX media playback. */
  public void play(ChessSound sound) {
    Objects.requireNonNull(sound, "sound must not be null");
    preload();
    if (isLoopingSound(sound)) submitLoop(sound, () -> clips.get(sound).play());
    else playAsync(sound);
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
    submitLoop(sound, () -> {
      AudioClip clip = clips.get(sound);
      clip.setVolume(volume);
      clip.setCycleCount(LOOP_FOREVER);
      clip.play();
    });
  }

  /** Stops all playback for the supplied effect, including an effect started in a loop. */
  public void stop(ChessSound sound) {
    Objects.requireNonNull(sound, "sound must not be null");
    if (!isLoopingSound(sound)) {
      asyncAudioExecutor.execute(() -> {
        Clip pcmClip = oneShotPcmClips.get(sound);
        if (pcmClip != null) pcmClip.stop();
        AudioClip fallback = clips.get(sound);
        if (fallback != null) fallback.stop();
      });
      return;
    }
    submitLoop(sound, () -> {
      AudioClip clip = clips.get(sound);
      if (clip != null) {
        clip.stop();
        clip.setCycleCount(1);
      }
    });
  }

  private AudioClip loadClip(ChessSound sound) {
    URL resource =
        Objects.requireNonNull(
            getClass().getResource(sound.resourcePath()),
            () -> "Missing chess sound resource: " + sound.resourcePath());
    return new AudioClip(resource.toExternalForm());
  }

  private void submitLoop(ChessSound sound, Runnable action) {
    loopAudioExecutor.execute(() -> {
      try {
        action.run();
      } catch (RuntimeException exception) {
        log.warn("loop_sound_failed sound={}", sound, exception);
      }
    });
  }

  @PreDestroy
  public void close() {
    asyncAudioExecutor.execute(() -> oneShotPcmClips.values().forEach(Clip::close));
    asyncAudioExecutor.shutdown();
    loopAudioExecutor.execute(() -> {
      for (ChessSound sound : ChessSound.values()) {
        if (isLoopingSound(sound)) clips.get(sound).stop();
      }
    });
    loopAudioExecutor.shutdown();
  }
}
