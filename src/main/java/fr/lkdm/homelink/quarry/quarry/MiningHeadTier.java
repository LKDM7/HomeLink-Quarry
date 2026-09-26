package fr.lkdm.homelink.quarry.quarry;

import java.util.Locale;

/**
 * Mining Head level. The head alone sets the drilling time of one block; it has no durability.
 */
public enum MiningHeadTier {
    I(1, 200),
    II(2, 120),
    III(3, 60);

    private final int level;
    private final int ticksPerBlock;

    MiningHeadTier(int level, int ticksPerBlock) {
        this.level = level;
        this.ticksPerBlock = ticksPerBlock;
    }

    public int level() {
        return level;
    }

    /** Server ticks needed to drill one block (20 ticks = 1 second). */
    public int ticksPerBlock() {
        return ticksPerBlock;
    }

    public int secondsPerBlock() {
        return ticksPerBlock / 20;
    }

    public String roman() {
        return name();
    }

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
