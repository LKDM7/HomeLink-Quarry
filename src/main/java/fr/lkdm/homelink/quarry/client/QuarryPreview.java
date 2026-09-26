package fr.lkdm.homelink.quarry.client;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Personal, client-only preview choices per quarry: nothing is sent to the server or to other players.
 * PREVIEW shows the area outline; the three options add layers, progress and the current target.
 */
public final class QuarryPreview {
    /** Display options of one quarry. */
    public static final class Options {
        public boolean area;
        public boolean layers;
        public boolean progress;

        public boolean any() {
            return area || layers || progress;
        }
    }

    private static final Map<GlobalPos, Options> OPTIONS = new HashMap<>();

    private QuarryPreview() {
    }

    public static Options get(ResourceKey<Level> dimension, BlockPos pos) {
        return OPTIONS.computeIfAbsent(GlobalPos.of(dimension, pos.immutable()), key -> new Options());
    }

    public static Map<GlobalPos, Options> all() {
        return OPTIONS;
    }

    /** Forgets every choice, for example when leaving a world. */
    public static void clear() {
        OPTIONS.clear();
    }
}
