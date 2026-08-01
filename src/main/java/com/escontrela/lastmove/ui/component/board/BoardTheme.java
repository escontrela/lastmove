package com.escontrela.lastmove.ui.component.board;

/**
 * Visual theme settings for the chess board, such as square colors and piece set.
 *
 * <p>Placeholder – theming support is planned for a future milestone.
 */
public enum BoardTheme {
    CLASSIC("#f0d9b5", "#b58863"),
    BLUE_GREY("#dee3e6", "#8ca2ad"),
    GREEN("#ffffdd", "#86a666");

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
