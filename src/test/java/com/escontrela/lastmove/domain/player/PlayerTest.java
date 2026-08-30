package com.escontrela.lastmove.domain.player;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class PlayerTest {

    @Test
    void createsValidPlayer() {
        Player player = Player.create("alice.smith@example.com", "Alice", "Smith", Optional.empty());

        assertEquals("alice.smith@example.com", player.email());
        assertEquals("Alice", player.firstName());
        assertEquals("Smith", player.lastName());
        assertEquals("Alice Smith", player.fullName());
        assertTrue(player.photo().isEmpty());
    }

    @Test
    void createsKnightshadeAsTheLocalApplicationBotIdentity() {
        Player bot = Player.knightshadeBot("knight-shade-bot");

        assertEquals("Knightshade Arena Bot", bot.fullName());
        assertEquals("LICHESS", bot.externalProvider().orElseThrow());
        assertEquals("knight-shade-bot", bot.externalAccountId().orElseThrow());
    }

    @Test
    void trimsWhitespace() {
        Player player = Player.create("  bob@example.com  ", "  Bob  ", "  Jones  ", Optional.empty());

        assertEquals("bob@example.com", player.email());
        assertEquals("Bob", player.firstName());
        assertEquals("Jones", player.lastName());
    }

    @Test
    void rejectsBlankEmail() {
        assertThrows(
                IllegalArgumentException.class,
                () -> Player.create("  ", "Alice", "Smith", Optional.empty()));
    }

    @Test
    void rejectsInvalidEmail() {
        assertThrows(
                IllegalArgumentException.class,
                () -> Player.create("not-an-email", "Alice", "Smith", Optional.empty()));
    }

    @Test
    void rejectsLongEmail() {
        String longEmail = "a".repeat(250) + "@example.com";
        assertThrows(
                IllegalArgumentException.class,
                () -> Player.create(longEmail, "Alice", "Smith", Optional.empty()));
    }

    @Test
    void rejectsBlankFirstName() {
        assertThrows(
                IllegalArgumentException.class,
                () -> Player.create("alice@example.com", "  ", "Smith", Optional.empty()));
    }

    @Test
    void rejectsLongFirstName() {
        assertThrows(
                IllegalArgumentException.class,
                () -> Player.create("alice@example.com", "a".repeat(51), "Smith", Optional.empty()));
    }

    @Test
    void rejectsBlankLastName() {
        assertThrows(
                IllegalArgumentException.class,
                () -> Player.create("alice@example.com", "Alice", "  ", Optional.empty()));
    }

    @Test
    void acceptsPngPhoto() {
        byte[] png = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47};

        Player player = Player.create("alice@example.com", "Alice", "Smith", Optional.of(png));

        assertArrayEquals(png, player.photo().orElseThrow());
    }

    @Test
    void acceptsJpegPhoto() {
        byte[] jpeg = new byte[] {(byte) 0xFF, (byte) 0xD8};

        Player player = Player.create("alice@example.com", "Alice", "Smith", Optional.of(jpeg));

        assertArrayEquals(jpeg, player.photo().orElseThrow());
    }

    @Test
    void rejectsEmptyPhoto() {
        assertThrows(
                IllegalArgumentException.class,
                () -> Player.create("alice@example.com", "Alice", "Smith", Optional.of(new byte[0])));
    }

    @Test
    void rejectsPhotoExceedingMaxSize() {
        byte[] huge = new byte[1_048_577];
        assertThrows(
                IllegalArgumentException.class,
                () -> Player.create("alice@example.com", "Alice", "Smith", Optional.of(huge)));
    }

    @Test
    void rejectsNonImagePhoto() {
        byte[] text = "not an image".getBytes();
        assertThrows(
                IllegalArgumentException.class,
                () -> Player.create("alice@example.com", "Alice", "Smith", Optional.of(text)));
    }
}
