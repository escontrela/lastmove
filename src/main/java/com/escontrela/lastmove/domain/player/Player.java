package com.escontrela.lastmove.domain.player;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/** Persisted player profile used to own games and analyses. */
public final class Player {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final int MAX_EMAIL_LENGTH = 255;
    private static final int MAX_NAME_LENGTH = 50;
    private static final int MAX_PHOTO_BYTES = 1_048_576;

    private final PlayerId id;
    private final String email;
    private final String firstName;
    private final String lastName;
    private final Optional<byte[]> photo;
    private final Instant createdAt;
    private final PlayerType type;
    private final Optional<String> externalProvider;
    private final Optional<String> externalAccountId;

    public Player(
            PlayerId id,
            String email,
            String firstName,
            String lastName,
            Optional<byte[]> photo,
            Instant createdAt) {
        this(id, email, firstName, lastName, photo, createdAt, PlayerType.HUMAN, Optional.empty(), Optional.empty());
    }

    public Player(
            PlayerId id,
            String email,
            String firstName,
            String lastName,
            Optional<byte[]> photo,
            Instant createdAt,
            PlayerType type,
            Optional<String> externalProvider,
            Optional<String> externalAccountId) {
        this.id = id;
        this.email = validateEmail(email);
        this.firstName = validateName(firstName, "firstName");
        this.lastName = validateName(lastName, "lastName");
        this.photo = validatePhoto(photo);
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.externalProvider = normalizeOptional(externalProvider, "externalProvider");
        this.externalAccountId = normalizeOptional(externalAccountId, "externalAccountId");
        if (this.externalProvider.isPresent() != this.externalAccountId.isPresent()) {
            throw new IllegalArgumentException("external provider and account id must coexist");
        }
        if (type == PlayerType.SYSTEM && this.externalProvider.isEmpty()) {
            throw new IllegalArgumentException("system players require an external identity");
        }
    }

    /** Creates a new, not-yet-persisted player profile. */
    public static Player create(
            String email, String firstName, String lastName, Optional<byte[]> photo) {
        return new Player(null, email, firstName, lastName, photo, Instant.now());
    }

    /** Creates the non-editable local representation of a Lichess bot account. */
    public static Player lichessBot(String accountId, String username) {
        String id = normalizeRequired(accountId, "accountId");
        String name = normalizeRequired(username, "username");
        return new Player(null, id + "@lichess.local", name, "Lichess Bot", Optional.empty(), Instant.now(),
                PlayerType.SYSTEM, Optional.of("LICHESS"), Optional.of(id));
    }

    /** Returns this persisted profile with its editable details replaced. */
    public Player update(String email, String firstName, String lastName, Optional<byte[]> photo) {
        if (id == null) {
            throw new IllegalStateException("Cannot update a player that has not been persisted");
        }
        if (type != PlayerType.HUMAN) {
            throw new IllegalStateException("System players are managed by their external identity");
        }
        return new Player(id, email, firstName, lastName, photo, createdAt, type, externalProvider, externalAccountId);
    }

    /** Refreshes the display name of an externally managed system player without changing its identity. */
    public Player refreshSystemDisplayName(String displayName) {
        if (id == null || type != PlayerType.SYSTEM) {
            throw new IllegalStateException("Only persisted system players can refresh their display name");
        }
        return new Player(id, email, displayName, lastName, photo, createdAt, type, externalProvider, externalAccountId);
    }

    public PlayerId id() {
        return id;
    }

    public String email() {
        return email;
    }

    public String firstName() {
        return firstName;
    }

    public String lastName() {
        return lastName;
    }

    public String fullName() {
        return firstName + " " + lastName;
    }

    public Optional<byte[]> photo() {
        return photo;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public PlayerType type() { return type; }

    public Optional<String> externalProvider() { return externalProvider; }

    public Optional<String> externalAccountId() { return externalAccountId; }

    private static String validateEmail(String email) {
        Objects.requireNonNull(email, "email must not be null");
        String trimmed = email.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("email must not be blank");
        }
        if (trimmed.length() > MAX_EMAIL_LENGTH) {
            throw new IllegalArgumentException(
                    "email must not exceed " + MAX_EMAIL_LENGTH + " characters");
        }
        if (!EMAIL_PATTERN.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("email is not valid");
        }
        return trimmed;
    }

    private static String validateName(String name, String fieldName) {
        Objects.requireNonNull(name, fieldName + " must not be null");
        String trimmed = name.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        if (trimmed.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException(
                    fieldName + " must not exceed " + MAX_NAME_LENGTH + " characters");
        }
        return trimmed;
    }

    private static Optional<byte[]> validatePhoto(Optional<byte[]> photo) {
        Objects.requireNonNull(photo, "photo must not be null");
        if (photo.isEmpty()) {
            return photo;
        }
        byte[] bytes = photo.get();
        if (bytes.length == 0) {
            throw new IllegalArgumentException("photo must not be empty");
        }
        if (bytes.length > MAX_PHOTO_BYTES) {
            throw new IllegalArgumentException(
                    "photo must not exceed " + (MAX_PHOTO_BYTES / 1024) + " KB");
        }
        if (!isPngOrJpeg(bytes)) {
            throw new IllegalArgumentException("photo must be a PNG or JPEG image");
        }
        return photo;
    }

    private static Optional<String> normalizeOptional(Optional<String> value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        return value.map(item -> normalizeRequired(item, field));
    }

    private static String normalizeRequired(String value, String field) {
        String normalized = Objects.requireNonNull(value, field + " must not be null").trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
        return normalized;
    }

    private static boolean isPngOrJpeg(byte[] bytes) {
        if (bytes.length >= 4) {
            boolean png = bytes[0] == (byte) 0x89
                    && bytes[1] == (byte) 0x50
                    && bytes[2] == (byte) 0x4E
                    && bytes[3] == (byte) 0x47;
            if (png) {
                return true;
            }
        }
        if (bytes.length >= 2) {
            return bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8;
        }
        return false;
    }
}
