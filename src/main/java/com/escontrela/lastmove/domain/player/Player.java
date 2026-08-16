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

    public Player(
            PlayerId id,
            String email,
            String firstName,
            String lastName,
            Optional<byte[]> photo,
            Instant createdAt) {
        this.id = id;
        this.email = validateEmail(email);
        this.firstName = validateName(firstName, "firstName");
        this.lastName = validateName(lastName, "lastName");
        this.photo = validatePhoto(photo);
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    /** Creates a new, not-yet-persisted player profile. */
    public static Player create(
            String email, String firstName, String lastName, Optional<byte[]> photo) {
        return new Player(null, email, firstName, lastName, photo, Instant.now());
    }

    /** Returns this persisted profile with its editable details replaced. */
    public Player update(String email, String firstName, String lastName, Optional<byte[]> photo) {
        if (id == null) {
            throw new IllegalStateException("Cannot update a player that has not been persisted");
        }
        return new Player(id, email, firstName, lastName, photo, createdAt);
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
