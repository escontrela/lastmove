package com.escontrela.lastmove.ui.component.profile;

import java.util.Locale;
import java.util.Objects;

/** Pure text formatting used by {@link CurrentUserAvatarControl}. */
final class CurrentUserAvatarText {

    private CurrentUserAvatarText() {}

    static String initialsFor(String name) {
        String trimmed = Objects.requireNonNullElse(name, "").trim();
        if (trimmed.isEmpty() || "unknown".equalsIgnoreCase(trimmed)) {
            return "?";
        }
        String[] words = trimmed.split("\\s+");
        if (words.length == 1) {
            return firstCharacter(words[0]);
        }
        return firstCharacter(words[0]) + firstCharacter(words[words.length - 1]);
    }

    private static String firstCharacter(String value) {
        return value.substring(0, value.offsetByCodePoints(0, 1)).toUpperCase(Locale.ROOT);
    }
}
