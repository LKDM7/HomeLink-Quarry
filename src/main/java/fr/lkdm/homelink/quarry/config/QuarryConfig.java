package fr.lkdm.homelink.quarry.config;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Server rules (per world) and client display preferences. */
public final class QuarryConfig {
    public static final ModConfigSpec SERVER_SPEC;
    public static final ModConfigSpec CLIENT_SPEC;

    public static final ModConfigSpec.ConfigValue<List<? extends String>> BLACKLIST;
    public static final ModConfigSpec.IntValue MAX_CONTROLLER_DISTANCE;
    public static final ModConfigSpec.IntValue POSITIONS_PER_TICK;
    public static final ModConfigSpec.IntValue TRANSFER_INTERVAL;
    public static final ModConfigSpec.IntValue ENERGY_LOW_THRESHOLD;
    public static final ModConfigSpec.IntValue ENERGY_PER_BLOCK;
    public static final ModConfigSpec.BooleanValue MINE_CONTAINERS;

    public static final ModConfigSpec.IntValue PREVIEW_RENDER_DISTANCE;

    private static volatile Set<Block> blacklist;

    static {
        ModConfigSpec.Builder server = new ModConfigSpec.Builder();
        BLACKLIST = server.comment("Blocks a quarry never mines, in addition to the block tag homelink_quarry:quarry_blacklist.",
                        "Unbreakable blocks (negative hardness) and fluids are always skipped.")
                .defineListAllowEmpty("quarryBlacklist", List.of("minecraft:bedrock", "minecraft:end_portal",
                        "minecraft:end_portal_frame", "minecraft:end_gateway", "minecraft:nether_portal",
                        "minecraft:command_block", "minecraft:chain_command_block", "minecraft:repeating_command_block",
                        "minecraft:structure_block", "minecraft:jigsaw", "minecraft:barrier", "minecraft:reinforced_deepslate"),
                        () -> "minecraft:bedrock", QuarryConfig::validBlockName);
        MAX_CONTROLLER_DISTANCE = server.comment("Largest horizontal distance, in blocks, between a controller and its area.")
                .defineInRange("quarryMaxControllerDistance", 64, 0, 256);
        POSITIONS_PER_TICK = server.comment("How many empty or skipped positions a quarry may pass over in one tick.",
                        "Drilling a real block always takes the Mining Head time.")
                .defineInRange("quarryPositionsPerTick", 64, 1, 1024);
        TRANSFER_INTERVAL = server.comment("Ticks between two stack transfers from the buffer to a connected ITEM_INPUT.")
                .defineInRange("quarryTransferInterval", 20, 1, 1200);
        ENERGY_LOW_THRESHOLD = server.comment("Charge percentage of the internal HE buffer at or below which the energy_low warning is raised.")
                .defineInRange("quarryEnergyLowThreshold", 15, 0, 100);
        ENERGY_PER_BLOCK = server.comment("HomeLink Energy (HE) used to drill one block, whatever the Mining Head. The quarry runs on HE only.",
                        "The head only sets how fast this energy is asked for; energy never makes the quarry faster.",
                        "The internal buffer holds 50 blocks of drilling. For scale: 1 coal = 4000 HE, a Solar Panel III makes 20000 HE a day.")
                .defineInRange("quarryEnergyPerBlock", 100, 1, 1_000_000);
        MINE_CONTAINERS = server.comment("Mine blocks that hold an inventory. Their contents go to the buffer with the block;",
                        "when the whole content cannot fit in an empty buffer the block is left in place.")
                .define("quarryMineContainers", true);
        SERVER_SPEC = server.build();

        ModConfigSpec.Builder client = new ModConfigSpec.Builder();
        PREVIEW_RENDER_DISTANCE = client.comment("Largest distance, in blocks, at which the quarry area preview is drawn.")
                .defineInRange("quarryPreviewRenderDistance", 64, 8, 256);
        CLIENT_SPEC = client.build();
    }

    private QuarryConfig() {
    }

    private static boolean validBlockName(Object value) {
        return value instanceof String name && ResourceLocation.tryParse(name) != null;
    }

    /** Configured blacklist resolved to blocks; unknown identifiers are ignored. */
    public static Set<Block> blacklist() {
        Set<Block> cached = blacklist;
        if (cached == null) {
            cached = BLACKLIST.get().stream().map(ResourceLocation::tryParse)
                    .filter(id -> id != null && BuiltInRegistries.BLOCK.containsKey(id))
                    .map(BuiltInRegistries.BLOCK::get).collect(Collectors.toUnmodifiableSet());
            blacklist = cached;
        }
        return cached;
    }

    public static void onConfigChanged(ModConfigEvent event) {
        if (event.getConfig().getSpec() == SERVER_SPEC) blacklist = null;
    }
}
