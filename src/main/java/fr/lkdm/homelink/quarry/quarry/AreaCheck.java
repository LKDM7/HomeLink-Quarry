package fr.lkdm.homelink.quarry.quarry;

import java.util.Locale;

/** Result of validating a configured area against the quarry level and the dimension. */
public enum AreaCheck {
    VALID,
    MISSING_CORNERS,
    TOO_LARGE,
    STOP_ABOVE_START,
    OUT_OF_WORLD,
    TOO_FAR,
    WRONG_DIMENSION;

    public boolean valid() {
        return this == VALID;
    }

    public String key() {
        return "area.homelink_quarry." + name().toLowerCase(Locale.ROOT);
    }
}
