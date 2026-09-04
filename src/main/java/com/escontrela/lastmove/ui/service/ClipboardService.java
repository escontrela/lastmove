package com.escontrela.lastmove.ui.service;

import java.util.Objects;
import java.util.Optional;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import org.springframework.stereotype.Component;

/** Writes presentation text to the operating system clipboard through JavaFX. */
@Component
public final class ClipboardService {

  /** Returns text currently available on the system clipboard, if any. */
  public Optional<String> text() {
    String value = Clipboard.getSystemClipboard().getString();
    return Optional.ofNullable(value).filter(text -> !text.isBlank());
  }

  /** Copies a non-null text value and reports whether the system clipboard accepted it. */
  public boolean copyText(String text) {
    ClipboardContent content = new ClipboardContent();
    content.putString(Objects.requireNonNull(text, "text must not be null"));
    return Clipboard.getSystemClipboard().setContent(content);
  }
}
