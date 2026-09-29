package fr.lkdm.homelink.quarry.homelink;

import fr.lkdm.homecore.api.action.ActionResult;
import fr.lkdm.homecore.api.action.DeviceAction;
import fr.lkdm.homecore.api.device.DashboardDevice;
import fr.lkdm.homecore.api.device.DeviceSchema;
import fr.lkdm.homecore.api.device.DeviceStatus;
import fr.lkdm.homecore.api.device.Renamable;
import fr.lkdm.homecore.api.device.Switchable;
import fr.lkdm.homecore.api.event.DeviceEvent;
import fr.lkdm.homecore.api.metric.DeviceMetric;
import fr.lkdm.homecore.api.metric.Duration;
import fr.lkdm.homecore.api.metric.MetricTypes;
import fr.lkdm.homecore.api.metric.Percentage;
import fr.lkdm.homecore.api.metric.Unit;
import fr.lkdm.homecore.api.metric.UpdatePolicy;
import fr.lkdm.homecore.api.network.HomeNetwork;
import fr.lkdm.homecore.api.network.NetworkMember;
import fr.lkdm.homecore.api.security.Permission;
import fr.lkdm.homelink.quarry.blockentity.QuarryControllerBlockEntity;
import fr.lkdm.homelink.quarry.config.QuarryConfig;
import fr.lkdm.homelink.quarry.quarry.QuarryArea;
import fr.lkdm.homelink.quarry.quarry.QuarryStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * HomeCore device of a Quarry Controller: metrics, the START / PAUSE / RESUME / STOP actions (run only
 * through HomeCore's authorized gateway) and events published on transitions, never once per tick.
 *
 * <p>Status: HomeCore only accepts actions on an ONLINE device, so a loaded quarry stays ONLINE and
 * carries its precise state in the message and in the {@code status} metric; waiting states are also
 * announced as events. A fault (ERROR) maps to ERROR and an unloaded quarry is OFFLINE.</p>
 */
public final class QuarryDevice implements DashboardDevice, NetworkMember, Renamable, Switchable {
    /** energy_low re-arms once the charge is back this many points above the threshold. */
    public static final int ENERGY_HYSTERESIS = 10;

    private final QuarryControllerBlockEntity quarry;
    private final UUID identity;
    private final Consumer<DeviceEvent> events;

    private final DeviceMetric<QuarryStatus> status = DeviceMetric.builder(QuarryIds.STATUS, name(QuarryIds.STATUS, "Status"),
            MetricTypes.enumeration(QuarryIds.STATUS, QuarryStatus.class), QuarryStatus.IDLE).updatePolicy(UpdatePolicy.ON_CHANGE).build();
    private final DeviceMetric<Integer> level = integer(QuarryIds.QUARRY_LEVEL, "Quarry level", Unit.NONE, 1, 3);
    // An invalid (too large) area is still reported with its real size, so no upper bound here.
    private final DeviceMetric<Integer> width = integer(QuarryIds.AREA_WIDTH, "Area width", Unit.BLOCK, 0, Integer.MAX_VALUE);
    private final DeviceMetric<Integer> length = integer(QuarryIds.AREA_LENGTH, "Area length", Unit.BLOCK, 0, Integer.MAX_VALUE);
    private final DeviceMetric<Integer> startY = integer(QuarryIds.START_Y, "Start Y", Unit.NONE, -2048, 2048);
    private final DeviceMetric<Integer> stopY = integer(QuarryIds.STOP_Y, "Stop Y", Unit.NONE, -2048, 2048);
    private final DeviceMetric<Integer> layer = integer(QuarryIds.CURRENT_LAYER, "Current layer", Unit.NONE, -2048, 2048);
    private final DeviceMetric<Percentage> progress = percentage(QuarryIds.PROGRESS, "Progress");
    private final DeviceMetric<Long> mined = count(QuarryIds.BLOCKS_MINED, "Blocks mined", Unit.BLOCK);
    private final DeviceMetric<Long> lifetime = count(QuarryIds.LIFETIME_BLOCKS_MINED, "Lifetime blocks mined", Unit.BLOCK);
    private final DeviceMetric<Long> remaining = count(QuarryIds.BLOCKS_REMAINING, "Blocks remaining", Unit.BLOCK);
    private final DeviceMetric<Integer> head = integer(QuarryIds.MINING_HEAD_LEVEL, "Mining head level", Unit.NONE, 0, 3);
    private final DeviceMetric<Double> speed = DeviceMetric.builder(QuarryIds.MINING_SPEED, name(QuarryIds.MINING_SPEED, "Seconds per block"),
            MetricTypes.DOUBLE, 0.0).unit(Unit.SECONDS).range(0, 10, 0).updatePolicy(UpdatePolicy.ON_CHANGE).build();
    private final DeviceMetric<Percentage> energy = percentage(QuarryIds.ENERGY_PERCENTAGE, "Energy");
    private final DeviceMetric<Duration> runtime = DeviceMetric.builder(QuarryIds.RUNTIME_REMAINING, name(QuarryIds.RUNTIME_REMAINING, "Runtime remaining"),
            MetricTypes.DURATION, new Duration(0)).unit(Unit.TICKS).updatePolicy(UpdatePolicy.ON_CHANGE).build();
    private final DeviceMetric<Long> estimate = count(QuarryIds.ESTIMATED_BLOCKS, "Estimated blocks with stored energy", Unit.BLOCK);
    private final DeviceMetric<Percentage> output = percentage(QuarryIds.OUTPUT_USAGE, "Output usage");
    private final DeviceMetric<Long> outputItems = count(QuarryIds.OUTPUT_ITEM_COUNT, "Items in output", Unit.ITEM);
    private final DeviceMetric<Boolean> storage = DeviceMetric.builder(QuarryIds.STORAGE_CONNECTED, name(QuarryIds.STORAGE_CONNECTED, "Storage output connected"),
            MetricTypes.BOOLEAN, false).updatePolicy(UpdatePolicy.ON_CHANGE).build();
    private final List<DeviceMetric<?>> metrics = List.of(status, level, width, length, startY, stopY, layer, progress, mined, lifetime,
            remaining, head, speed, energy, runtime, estimate, output, outputItems, storage);
    private final List<DeviceAction<?>> actions;
    private final DeviceSchema schema;

    // Transition tracking: the first observation after loading only records the state.
    private boolean observed;
    private QuarryStatus lastStatus;
    private boolean lastRunning;
    private boolean lastPaused;
    private boolean lastFinished;
    private boolean lastConnected;
    private boolean energyLowArmed = true;

    public QuarryDevice(QuarryControllerBlockEntity quarry, Consumer<DeviceEvent> events) {
        this.quarry = quarry;
        this.identity = quarry.deviceId();
        this.events = events;
        this.actions = List.of(
                button(QuarryIds.ACTION_START, "Start", "Start the job, or continue it after a stop.", quarry::start,
                        () -> Component.translatable("screen.homelink_quarry.cannot_start", Component.translatable(quarry.status().key()))),
                button(QuarryIds.ACTION_PAUSE, "Pause", "Freeze the quarry exactly where it is.", quarry::pause, () -> notNow("pause")),
                button(QuarryIds.ACTION_RESUME, "Resume", "Continue a paused job.", quarry::resume, () -> notNow("resume")),
                button(QuarryIds.ACTION_STOP, "Stop", "End the run; area, progress, head, energy and output are kept.", quarry::stop,
                        () -> notNow("stop")));
        this.schema = DeviceSchema.from(this);
        refresh();
    }

    private static Component notNow(String action) {
        return Component.translatableWithFallback("action.homelink_quarry." + action + ".refused", "Not possible in the current state");
    }

    private DeviceAction<fr.lkdm.homecore.api.action.Unit> button(ResourceLocation id, String label, String description,
                                                                  BooleanSupplier command, Supplier<Component> refusal) {
        return DeviceAction.button(id, Component.translatableWithFallback("action.homelink_quarry." + id.getPath(), label))
                .description(Component.translatableWithFallback("action.homelink_quarry." + id.getPath() + ".description", description))
                .requiredPermission(Permission.CONTROL.id())
                .handler((context, unit) -> {
                    if (!isValid() || !(quarry.getLevel() instanceof ServerLevel server) || !server.getServer().isSameThread())
                        return ActionResult.of(ActionResult.Code.DEVICE_OFFLINE);
                    if (!context.deviceId().equals(identity)) return ActionResult.of(ActionResult.Code.DENIED);
                    return command.getAsBoolean() ? ActionResult.success() : ActionResult.of(ActionResult.Code.FAILED, refusal.get());
                }).build();
    }

    private static Component name(ResourceLocation id, String fallback) {
        return Component.translatableWithFallback("metric.homelink_quarry." + id.getPath(), fallback);
    }

    private static DeviceMetric<Integer> integer(ResourceLocation id, String label, Unit unit, int min, int max) {
        return DeviceMetric.builder(id, name(id, label), MetricTypes.INTEGER, Math.max(min, Math.min(max, 0))).unit(unit).range(min, max, 1)
                .updatePolicy(UpdatePolicy.ON_CHANGE).build();
    }

    private static DeviceMetric<Long> count(ResourceLocation id, String label, Unit unit) {
        return DeviceMetric.builder(id, name(id, label), MetricTypes.LONG, 0L).unit(unit).updatePolicy(UpdatePolicy.ON_CHANGE).build();
    }

    private static DeviceMetric<Percentage> percentage(ResourceLocation id, String label) {
        return DeviceMetric.builder(id, name(id, label), MetricTypes.PERCENTAGE, new Percentage(0)).unit(Unit.PERCENT)
                .range(0, 100, 0).updatePolicy(UpdatePolicy.ON_CHANGE).build();
    }

    private boolean failureLogged;

    /**
     * Copies the quarry state into the metrics and publishes transition events. Server thread.
     * A HomeCore rejection is logged once and never stops the quarry or the server.
     */
    public void refresh() {
        try {
            update();
        } catch (RuntimeException failure) {
            if (!failureLogged) {
                failureLogged = true;
                com.mojang.logging.LogUtils.getLogger().error("HomeCore rejected an update of quarry device {}", identity, failure);
            }
        }
    }

    private void update() {
        QuarryArea area = quarry.area().orElse(null);
        QuarryStatus now = quarry.status();
        status.setValue(now);
        level.setValue(quarry.tier().level());
        width.setValue(area == null ? 0 : area.width());
        length.setValue(area == null ? 0 : area.length());
        startY.setValue(area == null ? 0 : area.startY());
        stopY.setValue(area == null ? 0 : area.stopY());
        layer.setValue(area == null || area.totalPositions() == 0 ? 0
                : area.layerY(Math.min(quarry.cursor(), area.totalPositions() - 1)));
        progress.setValue(new Percentage(Math.max(0, Math.min(100, quarry.progress() * 100))));
        mined.setValue(quarry.blocksMined());
        lifetime.setValue(quarry.lifetimeBlocksMined());
        remaining.setValue(quarry.positionsRemaining());
        head.setValue(quarry.headLevel());
        speed.setValue(quarry.miningHead().map(tier -> (double) tier.secondsPerBlock()).orElse(0.0));
        energy.setValue(new Percentage(quarry.energyPercent()));
        runtime.setValue(new Duration(quarry.runtimeTicks()));
        estimate.setValue(quarry.estimatedBlocks());
        output.setValue(new Percentage(quarry.bufferPercent()));
        outputItems.setValue(quarry.bufferItemCount());
        boolean connected = quarry.outputConnected();
        storage.setValue(connected);
        if (isValid()) publishTransitions(now, connected);
    }

    private void publishTransitions(QuarryStatus now, boolean connected) {
        boolean running = quarry.running();
        boolean paused = quarry.paused();
        boolean finished = quarry.finished();
        int energyPercent = quarry.energyPercent();
        int threshold = QuarryConfig.ENERGY_LOW_THRESHOLD.get();
        if (!observed) {
            observed = true;
            energyLowArmed = energyPercent > threshold;
        } else {
            List<DeviceEvent> out = new ArrayList<>();
            if (running && !lastRunning) out.add(event(QuarryIds.STARTED, DeviceEvent.Severity.INFO, Map.of()));
            if (!running && lastRunning && !finished) out.add(event(QuarryIds.STOPPED, DeviceEvent.Severity.INFO, Map.of()));
            if (running && paused && !lastPaused) out.add(event(QuarryIds.PAUSED, DeviceEvent.Severity.INFO, Map.of()));
            if (running && !paused && lastPaused && lastRunning) out.add(event(QuarryIds.RESUMED, DeviceEvent.Severity.INFO, Map.of()));
            if (finished && !lastFinished) out.add(event(QuarryIds.FINISHED, DeviceEvent.Severity.INFO,
                    Map.of("blocks_mined", Long.toString(quarry.blocksMined()))));
            if (now == QuarryStatus.NO_POWER && lastStatus != QuarryStatus.NO_POWER && running)
                out.add(event(QuarryIds.NO_POWER, DeviceEvent.Severity.WARNING, Map.of()));
            if (now == QuarryStatus.OUTPUT_FULL && lastStatus != QuarryStatus.OUTPUT_FULL)
                out.add(event(QuarryIds.OUTPUT_FULL, DeviceEvent.Severity.WARNING, Map.of("output_usage", Integer.toString(quarry.bufferPercent()))));
            if (lastStatus == QuarryStatus.OUTPUT_FULL && now != QuarryStatus.OUTPUT_FULL)
                out.add(event(QuarryIds.OUTPUT_AVAILABLE, DeviceEvent.Severity.INFO, Map.of()));
            if (now == QuarryStatus.BLOCKED && lastStatus != QuarryStatus.BLOCKED)
                out.add(event(QuarryIds.BLOCKED, DeviceEvent.Severity.CRITICAL, Map.of()));
            if (connected && !lastConnected) out.add(event(QuarryIds.STORAGE_CONNECTED_EVENT, DeviceEvent.Severity.INFO, Map.of()));
            if (!connected && lastConnected) out.add(event(QuarryIds.STORAGE_DISCONNECTED, DeviceEvent.Severity.WARNING, Map.of()));
            // One energy_low per crossing; recharging well above the threshold re-arms it.
            if (energyLowArmed && running && energyPercent <= threshold) {
                energyLowArmed = false;
                out.add(event(QuarryIds.ENERGY_LOW, DeviceEvent.Severity.WARNING, Map.of("energy_percentage", Integer.toString(energyPercent),
                        "runtime_ticks", Long.toString(quarry.runtimeTicks()))));
            } else if (!energyLowArmed && energyPercent >= Math.min(100, threshold + ENERGY_HYSTERESIS)) {
                energyLowArmed = true;
            }
            out.forEach(events);
        }
        lastStatus = now;
        lastRunning = running;
        lastPaused = paused;
        lastFinished = finished;
        lastConnected = connected;
    }

    private DeviceEvent event(ResourceLocation type, DeviceEvent.Severity severity, Map<String, String> data) {
        return new DeviceEvent(type, identity, Instant.now(), severity, data);
    }

    @Override public UUID id() { return identity; }
    @Override public ResourceLocation deviceType() { return QuarryIds.DEVICE_TYPE; }
    @Override public Component displayName() { return quarry.displayName().copy(); }
    @Override public ActionResult rename(String name) { quarry.setCustomName(name); return ActionResult.success(); }

    /** On while a job runs unpaused; switching off pauses it so that switching on resumes exactly there. */
    @Override public boolean powered() { return quarry.running() && !quarry.paused() && !quarry.finished(); }
    @Override public ActionResult setPowered(boolean powered) {
        if (powered == powered()) return ActionResult.success();
        if (!powered) return quarry.pause() ? ActionResult.success() : ActionResult.of(ActionResult.Code.FAILED, notNow("pause"));
        if (quarry.running() && quarry.paused()) return quarry.resume() ? ActionResult.success() : ActionResult.of(ActionResult.Code.FAILED, notNow("resume"));
        return quarry.start() ? ActionResult.success() : ActionResult.of(ActionResult.Code.FAILED,
                Component.translatable("screen.homelink_quarry.cannot_start", Component.translatable(quarry.status().key())));
    }

    // NetworkMember: lets a dashboard move the quarry between networks while its recorded binding stays in step.
    @Override public Optional<UUID> homeNetwork() { return quarry.homeNetwork(); }
    @Override public Optional<UUID> owner() { return quarry.owner(); }
    @Override public boolean canConfigure(ServerPlayer player) { return QuarryAccess.canConfigure(player, quarry); }
    @Override public void homeNetworkChanged(Optional<HomeNetwork> network) {
        network.ifPresentOrElse(value -> quarry.setHomeNetwork(value.id(), value.name()), quarry::clearHomeNetwork);
    }
    @Override public List<DeviceMetric<?>> metrics() { return metrics; }
    @Override public List<DeviceAction<?>> actions() { return actions; }
    @Override public Set<ResourceLocation> eventTypes() { return QuarryIds.EVENTS; }
    @Override public DeviceSchema schema() { return schema; }
    @Override public Optional<BlockPos> position() { return Optional.of(quarry.getBlockPos().immutable()); }
    @Override public Optional<ResourceKey<Level>> dimension() {
        return quarry.getLevel() == null ? Optional.empty() : Optional.of(quarry.getLevel().dimension());
    }

    @Override
    public DeviceStatus status() {
        if (!isValid()) return DeviceStatus.OFFLINE;
        QuarryStatus state = quarry.status();
        Component message = Component.translatableWithFallback(state.key(), state.name());
        return (state == QuarryStatus.ERROR ? DeviceStatus.ERROR : DeviceStatus.ONLINE).withMessage(message);
    }

    @Override
    public boolean isValid() {
        return !quarry.isRemoved() && quarry.getLevel() instanceof ServerLevel server && server.isLoaded(quarry.getBlockPos())
                && server.getBlockEntity(quarry.getBlockPos()) == quarry && identity.equals(quarry.deviceId());
    }
}
