package fr.lkdm.homelink.quarry.client;

import fr.lkdm.homelink.quarry.network.QuarryPayloads;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;

/** HomeNetwork choices received for open quarry screens (display only; the server decides). */
public final class QuarryClientData {
    private static final Map<BlockPos, List<QuarryPayloads.Choice>> CHOICES = new HashMap<>();

    private QuarryClientData() {
    }

    public static void setChoices(BlockPos pos, List<QuarryPayloads.Choice> choices) {
        if (CHOICES.size() > 16) CHOICES.clear();
        CHOICES.put(pos.immutable(), List.copyOf(choices));
    }

    public static List<QuarryPayloads.Choice> choices(BlockPos pos) {
        return CHOICES.getOrDefault(pos, List.of());
    }

    public static void clear() {
        CHOICES.clear();
    }
}
