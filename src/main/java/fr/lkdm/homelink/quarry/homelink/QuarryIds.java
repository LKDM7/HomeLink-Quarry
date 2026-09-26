package fr.lkdm.homelink.quarry.homelink;

import fr.lkdm.homelink.quarry.HomeLinkQuarry;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/** Stable HomeCore identifiers of the quarry device: type, metrics, actions and events. */
public final class QuarryIds {
    public static final ResourceLocation DEVICE_TYPE = id("quarry");

    public static final ResourceLocation STATUS = id("status");
    public static final ResourceLocation QUARRY_LEVEL = id("quarry_level");
    public static final ResourceLocation AREA_WIDTH = id("area_width");
    public static final ResourceLocation AREA_LENGTH = id("area_length");
    public static final ResourceLocation START_Y = id("start_y");
    public static final ResourceLocation STOP_Y = id("stop_y");
    public static final ResourceLocation CURRENT_LAYER = id("current_layer");
    public static final ResourceLocation PROGRESS = id("progress");
    public static final ResourceLocation BLOCKS_MINED = id("blocks_mined");
    public static final ResourceLocation LIFETIME_BLOCKS_MINED = id("lifetime_blocks_mined");
    public static final ResourceLocation BLOCKS_REMAINING = id("blocks_remaining");
    public static final ResourceLocation MINING_HEAD_LEVEL = id("mining_head_level");
    public static final ResourceLocation MINING_SPEED = id("mining_speed");
    public static final ResourceLocation FUEL_PERCENTAGE = id("fuel_percentage");
    public static final ResourceLocation RUNTIME_REMAINING = id("runtime_remaining");
    public static final ResourceLocation ESTIMATED_BLOCKS = id("estimated_blocks_with_fuel");
    public static final ResourceLocation OUTPUT_USAGE = id("output_usage");
    public static final ResourceLocation OUTPUT_ITEM_COUNT = id("output_item_count");
    public static final ResourceLocation STORAGE_CONNECTED = id("storage_connected");

    public static final ResourceLocation ACTION_START = id("start");
    public static final ResourceLocation ACTION_PAUSE = id("pause");
    public static final ResourceLocation ACTION_RESUME = id("resume");
    public static final ResourceLocation ACTION_STOP = id("stop");

    public static final ResourceLocation STARTED = id("started");
    public static final ResourceLocation PAUSED = id("paused");
    public static final ResourceLocation RESUMED = id("resumed");
    public static final ResourceLocation STOPPED = id("stopped");
    public static final ResourceLocation FINISHED = id("finished");
    public static final ResourceLocation FUEL_LOW = id("fuel_low");
    public static final ResourceLocation FUEL_EMPTY = id("fuel_empty");
    public static final ResourceLocation OUTPUT_FULL = id("output_full");
    public static final ResourceLocation OUTPUT_AVAILABLE = id("output_available");
    public static final ResourceLocation STORAGE_CONNECTED_EVENT = id("storage_connected");
    public static final ResourceLocation STORAGE_DISCONNECTED = id("storage_disconnected");
    public static final ResourceLocation BLOCKED = id("blocked");

    public static final Set<ResourceLocation> EVENTS = Set.of(STARTED, PAUSED, RESUMED, STOPPED, FINISHED, FUEL_LOW, FUEL_EMPTY,
            OUTPUT_FULL, OUTPUT_AVAILABLE, STORAGE_CONNECTED_EVENT, STORAGE_DISCONNECTED, BLOCKED);

    private QuarryIds() {
    }

    private static ResourceLocation id(String path) {
        return HomeLinkQuarry.id(path);
    }
}
