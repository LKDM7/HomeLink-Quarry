package fr.lkdm.homelink.quarry.quarry;

import com.mojang.serialization.Codec;
import java.util.Locale;
import net.minecraft.util.StringRepresentable;

/**
 * Quarry level. The level only bounds the horizontal mining area; speed comes from the Mining Head.
 */
public enum QuarryTier implements StringRepresentable {
    I(1, 8),
    II(2, 16),
    III(3, 32);

    public static final Codec<QuarryTier> CODEC = StringRepresentable.fromEnum(QuarryTier::values);

    private final int level;
    private final int maxSide;

    QuarryTier(int level, int maxSide) {
        this.level = level;
        this.maxSide = maxSide;
    }

    public int level() {
        return level;
    }

    /** Largest allowed width and length, in blocks. */
    public int maxSide() {
        return maxSide;
    }

    /** True when a width × length area fits this level, in either orientation. */
    public boolean allows(int width, int length) {
        return width >= 1 && length >= 1 && width <= maxSide && length <= maxSide;
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
