package com.escontrela.lastmove.ui.model;

/** The shared visual mode for the LastMove desktop shell. */
public enum ApplicationThemeMode {
    DAY,
    NIGHT;

    public boolean isNightMode() {
        return this == NIGHT;
    }
}
