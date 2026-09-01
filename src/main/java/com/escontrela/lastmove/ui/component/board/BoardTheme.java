package com.escontrela.lastmove.ui.component.board;

/**
 * Visual theme settings for the chess board, such as square colors and piece set.
 *
 * <p>The default palette follows the application's restrained blue visual language.
 */
public enum BoardTheme {
    LASTMOVE("#e9ebee", "#9ca3aa"),
    CLASSIC("#f0d9b5", "#b58863"),
    BLUE_GREY("#dee3e6", "#8ca2ad"),
    GREEN("#ffffdd", "#86a666"),
    V2("#efd7bd", "#715f59"),
    V2_GRAY("#e7eaed", "#aab5c2");

    private final String lightColor;
    private final String darkColor;

    BoardTheme(String lightColor, String darkColor) {
        this.lightColor = lightColor;
        this.darkColor = darkColor;
    }

    public String getLightColor() {
        return lightColor;
    }

    public String getDarkColor() {
        return darkColor;
    }
}
