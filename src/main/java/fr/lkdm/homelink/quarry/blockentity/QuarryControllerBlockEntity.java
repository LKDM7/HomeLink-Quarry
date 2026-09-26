package fr.lkdm.homelink.quarry.blockentity;

import fr.lkdm.homelink.quarry.block.DrillLightBlock;
import fr.lkdm.homelink.quarry.block.QuarryControllerBlock;
import fr.lkdm.homelink.quarry.config.QuarryConfig;
import fr.lkdm.homelink.quarry.item.MiningHeadItem;
import fr.lkdm.homelink.quarry.quarry.AreaCheck;
import fr.lkdm.homelink.quarry.quarry.MiningHeadTier;
import fr.lkdm.homelink.quarry.quarry.QuarryArea;
import fr.lkdm.homelink.quarry.quarry.QuarryFuel;
import fr.lkdm.homelink.quarry.quarry.QuarryMiner;
import fr.lkdm.homelink.quarry.quarry.QuarryOutputPort;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.sounds.SoundEvents;
import com.mojang.logging.LogUtils;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import fr.lkdm.homelink.quarry.homelink.QuarryAccess;
import fr.lkdm.homelink.quarry.homelink.QuarryDevice;
import fr.lkdm.homelink.quarry.homelink.QuarryHomeCore;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import fr.lkdm.homelink.quarry.quarry.QuarryStatus;
import fr.lkdm.homelink.quarry.quarry.QuarryTier;
import fr.lkdm.homelink.quarry.registry.QuarryRegistries;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Server-side state of a Quarry Controller. The level is derived from the block, never stored.
 * Progress is a single cursor over the area (see {@link QuarryArea}); nothing is precomputed.
 */
public class QuarryControllerBlockEntity extends BlockEntity {
    private final ItemStackHandler head = new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return stack.getItem() instanceof MiningHeadItem;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        protected void onContentsChanged(int slot) {
            onStateChanged();
        }
    };

    private final ItemStackHandler fuel = new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return QuarryFuel.isFuel(stack);
        }

        @Override
        protected void onContentsChanged(int slot) {
            onStateChanged();
        }
    };

    public static final int BUFFER_SLOTS = 27;

    /** Every drop goes through this buffer; automation may only extract from it. */
    private final ItemStackHandler buffer = new ItemStackHandler(BUFFER_SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if (level != null && !level.isClientSide) level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        }
    };

    private final QuarryAutomationHandler automation = new QuarryAutomationHandler(fuel, buffer);

    /** Running ticks left in the internal tank. */
    private int fuelTicks;

    /** Player-chosen name; empty for the default level name. */
    private String customName = "";
    /** Stable HomeCore identity; survives restarts and reloads. */
    private UUID deviceId = UUID.randomUUID();
    @Nullable private UUID owner;
    private String ownerName = "";
    @Nullable private UUID homeNetwork;
    private String homeNetworkName = "";
    @Nullable private QuarryDevice device;
    private int deviceRetry;
    private boolean clientHasNetwork;

    @Nullable private BlockPos cornerA;
    @Nullable private BlockPos cornerB;
    @Nullable private Integer stopY;
    /** Next position of the job, in [0, area.totalPositions()]. */
    private long cursor;
    private long blocksMined;
    private long lifetimeBlocksMined;
    /** The player started the job and did not stop it. */
    private boolean running;
    /** Explicit player pause; never cleared automatically. */
    private boolean paused;
    private boolean finished;
    private QuarryStatus status = QuarryStatus.IDLE;
    /** Why a running quarry currently waits (OUTPUT_FULL, NO_FUEL, BLOCKED); recomputed every tick, never saved. */
    @Nullable private QuarryStatus waiting;
    private int drillTicks;
    private int clientTicksPerBlock;
    private int clientHeadLevel;
    private AreaCheck clientAreaCheck = AreaCheck.MISSING_CORNERS;
    private long syncedCursor = -1;
    @Nullable private BlockPos crackPos;
    private int crackStage = -1;
    private boolean portDirty = true;
    private int portAnimation;
    /** Cell lit by the working Mining Head (torch level); never saved, the light expires by itself. */
    @Nullable private BlockPos lightPos;
    private static final Logger LOGGER = LogUtils.getLogger();

    public QuarryControllerBlockEntity(BlockPos pos, BlockState state) {
        super(QuarryRegistries.QUARRY_CONTROLLER.get(), pos, state);
    }

    public QuarryTier tier() {
        return getBlockState().getBlock() instanceof QuarryControllerBlock block ? block.tier() : QuarryTier.I;
    }

    public ItemStackHandler headSlot() {
        return head;
    }

    /** Installed head; its level alone decides the drilling speed. */
    public Optional<MiningHeadTier> miningHead() {
        return head.getStackInSlot(0).getItem() instanceof MiningHeadItem item ? Optional.of(item.tier()) : Optional.empty();
    }

    // ---- Fuel -----------------------------------------------------------------------------------

    public ItemStackHandler fuelSlot() {
        return fuel;
    }

    public ItemStackHandler buffer() {
        return buffer;
    }

    public int bufferUsedSlots() {
        int used = 0;
        for (int slot = 0; slot < buffer.getSlots(); slot++) if (!buffer.getStackInSlot(slot).isEmpty()) used++;
        return used;
    }

    /** Buffer fill level in [0, 100], counting partial stacks like a comparator does. */
    public int bufferPercent() {
        double fill = 0;
        for (int slot = 0; slot < buffer.getSlots(); slot++) {
            ItemStack stack = buffer.getStackInSlot(slot);
            if (!stack.isEmpty()) fill += (double) stack.getCount() / Math.min(buffer.getSlotLimit(slot), stack.getMaxStackSize());
        }
        return (int) Math.round(fill * 100 / buffer.getSlots());
    }

    /** Whether a compatible ITEM_INPUT sits against the ITEM_OUTPUT port. */
    public boolean outputConnected() {
        return getBlockState().hasProperty(QuarryControllerBlock.PORT) && getBlockState().getValue(QuarryControllerBlock.PORT) == 2;
    }

    /** Asks for a port check on the next server tick (placement, neighbour change, load). */
    public void markPortDirty() {
        portDirty = true;
    }

    /**
     * Detects the ITEM_INPUT behind the controller. Connecting plays a short sequence: the port lights up,
     * then about half a second later the connector appears with a click. Disconnecting is immediate.
     */
    private void updatePort(ServerLevel level) {
        boolean present = QuarryOutputPort.input(level, worldPosition, facing()) != null;
        int port = getBlockState().getValue(QuarryControllerBlock.PORT);
        if (present && port == 0) {
            setPort(level, 1);
            portAnimation = 10;
            effects(level, true, false);
        } else if (!present && port != 0) {
            setPort(level, 0);
            portAnimation = 0;
            effects(level, false, false);
            if (device != null) device.refresh();
        }
    }

    private void tickPortAnimation(ServerLevel level) {
        if (portAnimation <= 0 || --portAnimation > 0) return;
        if (QuarryOutputPort.input(level, worldPosition, facing()) == null) {
            setPort(level, 0);
            return;
        }
        setPort(level, 2);
        effects(level, true, true);
        if (device != null) device.refresh();
    }

    private void setPort(ServerLevel level, int value) {
        level.setBlock(worldPosition, getBlockState().setValue(QuarryControllerBlock.PORT, value), Block.UPDATE_CLIENTS);
    }

    private void effects(ServerLevel level, boolean connect, boolean locked) {
        Direction back = QuarryOutputPort.portSide(facing());
        double x = worldPosition.getX() + 0.5 + back.getStepX() * 0.55;
        double y = worldPosition.getY() + 0.5;
        double z = worldPosition.getZ() + 0.5 + back.getStepZ() * 0.55;
        if (!connect) {
            level.playSound(null, worldPosition, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.4F, 0.9F);
            level.sendParticles(ParticleTypes.SMOKE, x, y, z, 3, 0.08, 0.08, 0.08, 0.005);
        } else if (!locked) {
            level.playSound(null, worldPosition, SoundEvents.COPPER_BULB_TURN_ON, SoundSource.BLOCKS, 0.5F, 1.4F);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 5, 0.12, 0.12, 0.12, 0.02);
        } else {
            level.playSound(null, worldPosition, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.45F, 1.6F);
            level.sendParticles(new DustParticleOptions(new org.joml.Vector3f(0.85F, 0.51F, 0.29F), 0.7F), x, y, z, 6, 0.1, 0.1, 0.1, 0);
            level.sendParticles(new DustParticleOptions(new org.joml.Vector3f(0.93F, 0.93F, 0.9F), 0.5F), x, y, z, 3, 0.1, 0.1, 0.1, 0);
        }
    }

    private Direction facing() {
        return getBlockState().getValue(QuarryControllerBlock.FACING);
    }

    /** Moves one buffer stack per transfer interval into the connected ITEM_INPUT. */
    private void transfer(ServerLevel level) {
        if (!outputConnected() || Math.floorMod(level.getGameTime() + worldPosition.hashCode(), QuarryConfig.TRANSFER_INTERVAL.get()) != 0) return;
        if (bufferUsedSlots() == 0) return;
        var target = QuarryOutputPort.input(level, worldPosition, facing());
        if (target == null) {
            portDirty = true;
            return;
        }
        QuarryOutputPort.transferOneStack(buffer, target);
    }

    public String customName() {
        return customName;
    }

    /** Shown name: the custom name, or "Quarry I/II/III". */
    public Component displayName() {
        return customName.isEmpty() ? getBlockState().getBlock().getName() : Component.literal(customName);
    }

    /** Stores a sanitized name (no formatting codes or control characters, bounded length). */
    public void setCustomName(String name) {
        String clean = name.replace("§", "").codePoints().filter(c -> !Character.isISOControl(c))
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString().strip();
        if (clean.length() > fr.lkdm.homelink.quarry.network.QuarryPayloads.MAX_NAME_LENGTH)
            clean = clean.substring(0, fr.lkdm.homelink.quarry.network.QuarryPayloads.MAX_NAME_LENGTH);
        if (clean.equals(customName)) return;
        customName = clean;
        setChanged();
        sync();
    }

    /** Whether this player may rename or reconfigure the quarry (owner, operator, CONFIGURE member). */
    public boolean mayConfigure(Player player) {
        return player instanceof ServerPlayer server && QuarryAccess.canConfigure(server, this);
    }

    /** Whether this player may open the screen and take the output (anyone who may control it). */
    public boolean mayView(Player player) {
        return mayControl(player);
    }

    /** Whether this player may operate the quarry from its screen (control or configure rights). */
    public boolean mayControl(Player player) {
        return player instanceof ServerPlayer server && QuarryAccess.canControl(server, this);
    }

    // ---- HomeCore identity and network ----------------------------------------------------------

    public UUID deviceId() {
        return deviceId;
    }

    /** A copied controller collided with a live device: become a distinct device, outside any network. */
    public void resetIdentityAfterCollision() {
        deviceId = UUID.randomUUID();
        homeNetwork = null;
        homeNetworkName = "";
        setChanged();
        sync();
    }

    public Optional<UUID> owner() {
        return Optional.ofNullable(owner);
    }

    public void setOwner(UUID owner, String name) {
        this.owner = owner;
        this.ownerName = name;
        setChanged();
    }

    public Optional<UUID> homeNetwork() {
        return Optional.ofNullable(homeNetwork);
    }

    /** Network name when it was attached (display only; HomeCore holds the truth). */
    public String homeNetworkName() {
        return homeNetworkName;
    }

    /** Client view: whether the quarry belongs to a HomeNetwork (for the screen). */
    public boolean hasHomeNetwork() {
        return level != null && level.isClientSide ? clientHasNetwork : homeNetwork != null;
    }

    public void setHomeNetwork(UUID network, String name) {
        homeNetwork = network;
        homeNetworkName = name;
        setChanged();
        sync();
    }

    public void clearHomeNetwork() {
        homeNetwork = null;
        homeNetworkName = "";
        setChanged();
        sync();
    }

    /** The live HomeCore device, while registered. */
    public Optional<QuarryDevice> device() {
        return Optional.ofNullable(device);
    }

    private void ensureDevice(ServerLevel level) {
        if (device != null || isRemoved() || deviceRetry-- > 0) return;
        device = QuarryHomeCore.register(level, this).orElse(null);
        if (device == null) deviceRetry = 100;
    }

    private void releaseDevice() {
        if (device != null && level instanceof ServerLevel server) QuarryHomeCore.unregister(server, device);
        device = null;
    }

    /** The controller block was destroyed (not unloaded): leave its HomeNetwork. */
    public void destroyed() {
        if (level instanceof ServerLevel server) QuarryHomeCore.forgetOnRemoval(server, this);
        if (level != null) removeDrillLight(level);
        releaseDevice();
    }

    // ---- Drill light ----------------------------------------------------------------------------

    /** Cell currently lit by the Mining Head, if any. */
    @Nullable public BlockPos drillLight() {
        return lightPos;
    }

    /**
     * While the head drills, its cell (just above the target) emits torch-level light, following the head.
     * The light is only placed in air, renewed every second and removed as soon as the quarry stops working;
     * if the quarry cannot remove it (unloaded, crashed), it expires by itself after a few seconds.
     */
    private void updateDrillLight(ServerLevel level) {
        BlockPos target = status == QuarryStatus.MINING ? currentTarget() : null;
        BlockPos wanted = target == null ? null : target.above();
        if (wanted == null) {
            removeDrillLight(level);
            return;
        }
        BlockState current = level.isLoaded(wanted) ? level.getBlockState(wanted) : null;
        if (current != null && current.is(QuarryRegistries.DRILL_LIGHT.get()) && wanted.equals(lightPos)) {
            if (current.getValue(DrillLightBlock.AGE) > 0 && Math.floorMod(level.getGameTime() + worldPosition.hashCode(), 20) == 0)
                level.setBlock(wanted, current.setValue(DrillLightBlock.AGE, 0), Block.UPDATE_CLIENTS);
            return;
        }
        removeDrillLight(level);
        if (current != null && current.isAir() && level.getFluidState(wanted).isEmpty()) {
            level.setBlock(wanted, QuarryRegistries.DRILL_LIGHT.get().defaultBlockState(), Block.UPDATE_ALL);
            lightPos = wanted;
        }
    }

    private void removeDrillLight(Level level) {
        if (lightPos != null && level.isLoaded(lightPos) && level.getBlockState(lightPos).is(QuarryRegistries.DRILL_LIGHT.get())) {
            level.removeBlock(lightPos, false);
        }
        lightPos = null;
    }

    public long bufferItemCount() {
        long total = 0;
        for (int slot = 0; slot < buffer.getSlots(); slot++) total += buffer.getStackInSlot(slot).getCount();
        return total;
    }

    /** Hopper, pipe and ITEM_INPUT facing access. */
    public IItemHandler automation() {
        return automation;
    }

    public int fuelTicks() {
        return fuelTicks;
    }

    /** Fuel supply (tank plus waiting fuel) against one full tank, in [0, 100]. */
    public int fuelPercent() {
        return (int) Math.min(100, Math.round(runtimeTicks() * 100.0 / QuarryFuel.CAPACITY));
    }

    /** Running ticks available from the tank and the fuel waiting in the slot. */
    public long runtimeTicks() {
        ItemStack waiting = fuel.getStackInSlot(0);
        return fuelTicks + (long) QuarryFuel.burnTicks(waiting) * waiting.getCount();
    }

    /** Blocks the installed head can still drill with all available fuel; 0 without a head. */
    public long estimatedBlocks() {
        return miningHead().map(tier -> QuarryFuel.estimatedBlocks(runtimeTicks(), tier)).orElse(0L);
    }

    public boolean hasFuel() {
        return fuelTicks > 0 || QuarryFuel.isFuel(fuel.getStackInSlot(0));
    }

    /**
     * Moves one fuel item into the tank when it fits, like a furnace would burn it.
     * Container items such as the empty bucket of a lava bucket stay in the slot.
     */
    public boolean refuel() {
        ItemStack stack = fuel.getStackInSlot(0);
        int burn = QuarryFuel.burnTicks(stack);
        if (burn <= 0 || (fuelTicks > 0 && fuelTicks + burn > QuarryFuel.CAPACITY)) return false;
        ItemStack remainder = stack.getCraftingRemainingItem();
        if (!remainder.isEmpty() && stack.getCount() > 1) return false;
        fuelTicks += burn;
        fuel.setStackInSlot(0, remainder.isEmpty() ? stack.copyWithCount(stack.getCount() - 1) : remainder.copy());
        setChanged();
        return true;
    }

    /** Uses one running tick; refuels first when the tank is empty. */
    protected boolean consumeFuelTick() {
        if (fuelTicks <= 0 && !refuel()) return false;
        fuelTicks--;
        if (fuelTicks == 0) refuel();
        return true;
    }

    // ---- Area and depth -------------------------------------------------------------------------

    @Nullable public BlockPos cornerA() {
        return cornerA;
    }

    @Nullable public BlockPos cornerB() {
        return cornerB;
    }

    public Optional<QuarryArea> area() {
        if (cornerA == null || cornerB == null) return Optional.empty();
        int top = Math.max(cornerA.getY(), cornerB.getY());
        return Optional.of(QuarryArea.of(cornerA, cornerB, stopY == null ? defaultStopY(top) : stopY));
    }

    private int defaultStopY(int startY) {
        return level == null ? startY : Math.min(startY, level.getMinBuildHeight());
    }

    public AreaCheck checkArea() {
        if (cornerA == null || cornerB == null) return AreaCheck.MISSING_CORNERS;
        QuarryArea area = area().orElseThrow();
        if (!tier().allows(area.width(), area.length())) return AreaCheck.TOO_LARGE;
        if (area.stopY() > area.startY()) return AreaCheck.STOP_ABOVE_START;
        if (level != null && (area.stopY() < level.getMinBuildHeight() || area.startY() >= level.getMaxBuildHeight()))
            return AreaCheck.OUT_OF_WORLD;
        if (area.horizontalDistance(worldPosition) > QuarryConfig.MAX_CONTROLLER_DISTANCE.get()) return AreaCheck.TOO_FAR;
        return AreaCheck.VALID;
    }

    /** True while the configuration is locked by an unfinished, started job. */
    public boolean configurationLocked() {
        return running && !finished;
    }

    /** Applies both corners; refused while a job runs. A new area starts a new job. */
    public boolean setCorners(BlockPos a, BlockPos b) {
        if (configurationLocked()) return false;
        cornerA = a.immutable();
        cornerB = b.immutable();
        if (stopY != null && stopY > Math.max(a.getY(), b.getY())) stopY = null;
        resetJob();
        return true;
    }

    /** Sets Stop Y, clamped to the dimension and to Start Y; refused while a job runs. */
    public boolean setStopY(int y) {
        if (configurationLocked() || level == null) return false;
        int top = area().map(QuarryArea::startY).orElse(level.getMaxBuildHeight() - 1);
        stopY = Math.max(level.getMinBuildHeight(), Math.min(top, y));
        resetJob();
        return true;
    }

    private void resetJob() {
        cursor = 0;
        blocksMined = 0;
        drillTicks = 0;
        finished = false;
        running = false;
        paused = false;
        onStateChanged();
    }

    // ---- Job control ----------------------------------------------------------------------------

    /** First reason preventing START, or null when the quarry may start. */
    @Nullable public QuarryStatus startBlocker() {
        AreaCheck check = checkArea();
        if (check == AreaCheck.MISSING_CORNERS) return QuarryStatus.IDLE;
        if (!check.valid()) return QuarryStatus.INVALID_AREA;
        if (miningHead().isEmpty()) return QuarryStatus.NO_HEAD;
        if (!hasFuel()) return QuarryStatus.NO_FUEL;
        return null;
    }

    public boolean start() {
        if (running && !finished) return false;
        if (startBlocker() != null) return false;
        if (finished) {
            cursor = 0;
            blocksMined = 0;
            drillTicks = 0;
            finished = false;
        }
        running = true;
        paused = false;
        onStateChanged();
        return true;
    }

    public boolean pause() {
        if (!running || paused || finished) return false;
        paused = true;
        onStateChanged();
        return true;
    }

    public boolean resume() {
        if (!running || !paused) return false;
        paused = false;
        onStateChanged();
        return true;
    }

    /** Ends the run but keeps area, Stop Y, progress, head, fuel and buffer; START continues the job. */
    public boolean stop() {
        if (!running) return false;
        running = false;
        drillTicks = 0;
        paused = false;
        onStateChanged();
        return true;
    }

    public boolean running() {
        return running;
    }

    public boolean paused() {
        return paused;
    }

    public boolean finished() {
        return finished;
    }

    public long cursor() {
        return cursor;
    }

    public long blocksMined() {
        return blocksMined;
    }

    public long lifetimeBlocksMined() {
        return lifetimeBlocksMined;
    }

    /** Next position of the job, or null when there is none. */
    @Nullable public BlockPos currentTarget() {
        return area().filter(area -> !finished && cursor < area.totalPositions()).map(area -> area.target(cursor)).orElse(null);
    }

    /** Work done over valid work, in [0, 1]. Skipped positions count as processed. */
    public double progress() {
        return area().map(area -> area.totalPositions() == 0 ? 0 : Math.min(1.0, (double) cursor / area.totalPositions())).orElse(0.0);
    }

    public long positionsRemaining() {
        return area().map(area -> Math.max(0, area.totalPositions() - cursor)).orElse(0L);
    }

    public QuarryStatus status() {
        return status;
    }

    /** Moves the cursor past one processed position; finishes the job at the end of the area. */
    protected void advance() {
        QuarryArea area = area().orElse(null);
        if (area == null) return;
        cursor++;
        if (cursor >= area.totalPositions()) {
            cursor = area.totalPositions();
            finished = true;
        }
        setChanged();
    }

    /** Ticks already spent drilling the current target. */
    public int drillTicks() {
        return drillTicks;
    }

    /** Area validation as computed by the server; usable on the client, which has no server config. */
    public AreaCheck areaCheckClient() {
        return level != null && level.isClientSide ? clientAreaCheck : checkArea();
    }

    public boolean checkAreaClient() {
        return areaCheckClient().valid();
    }

    /** Head level known by the client renderer (0 = none); the server reads the slot directly. */
    public int headLevel() {
        return level != null && level.isClientSide ? clientHeadLevel : miningHead().map(MiningHeadTier::level).orElse(0);
    }

    /** Drilling time of the installed head, 0 without a head. */
    public int ticksPerBlock() {
        return miningHead().map(MiningHeadTier::ticksPerBlock).orElse(clientTicksPerBlock);
    }

    /** Recomputes the observable status from the current facts. */
    protected QuarryStatus computeStatus() {
        if (finished) return QuarryStatus.FINISHED;
        if (!running) {
            QuarryStatus blocker = startBlocker();
            return blocker == null ? QuarryStatus.READY : blocker;
        }
        if (paused) return QuarryStatus.PAUSED;
        if (!checkArea().valid()) return QuarryStatus.INVALID_AREA;
        if (miningHead().isEmpty()) return QuarryStatus.NO_HEAD;
        if (waiting != null) return waiting;
        if (!hasFuel()) return QuarryStatus.NO_FUEL;
        return QuarryStatus.MINING;
    }

    protected void onStateChanged() {
        if (level != null && level.isClientSide) return;
        status = computeStatus();
        if (status != QuarryStatus.MINING && drillTicks > 0 && !(running && !paused && waiting == QuarryStatus.OUTPUT_FULL)) {
            clearCracks();
        }
        setChanged();
        // Area, depth, head or fuel may have changed even when the status did not: the client needs them.
        sync();
        if (device != null) device.refresh();
    }

    // ---- Mining ---------------------------------------------------------------------------------

    public static void serverTick(Level level, BlockPos pos, BlockState state, QuarryControllerBlockEntity entity) {
        if (level instanceof ServerLevel server) entity.work(server);
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, QuarryControllerBlockEntity entity) {
        if (entity.status == QuarryStatus.MINING && entity.drillTicks < entity.clientTicksPerBlock) entity.drillTicks++;
    }

    private void work(ServerLevel level) {
        ensureDevice(level);
        if (portDirty) {
            portDirty = false;
            updatePort(level);
        }
        tickPortAnimation(level);
        transfer(level);
        QuarryStatus previous = status;
        QuarryStatus waitingBefore = waiting;
        waiting = null;
        if (running && !paused && !finished) {
            try {
                mineStep(level);
            } catch (RuntimeException failure) {
                LOGGER.error("Quarry at {} stopped after an unexpected error", worldPosition, failure);
                running = false;
                clearCracks();
                status = QuarryStatus.ERROR;
                setChanged();
                sync();
                if (device != null) device.refresh();
                return;
            }
        }
        status = computeStatus();
        if (status != QuarryStatus.MINING && waiting != QuarryStatus.OUTPUT_FULL) clearCracks();
        updateDrillLight(level);
        // Metrics once a second, and at once on any status change (events are transition based).
        if (device != null && (status != previous || Math.floorMod(level.getGameTime() + worldPosition.hashCode(), 20) == 0)) device.refresh();
        boolean moved = cursor != syncedCursor && (drillTicks <= 1 || level.getGameTime() % 10 == 0);
        if (status != previous || waiting != waitingBefore || moved) sync();
    }

    /** Processes at most one drilled block and a bounded number of passed-over positions. */
    private void mineStep(ServerLevel level) {
        QuarryArea area = area().orElse(null);
        MiningHeadTier headTier = miningHead().orElse(null);
        if (area == null || headTier == null || !checkArea().valid()) return;
        int budget = QuarryConfig.POSITIONS_PER_TICK.get();
        while (!finished && budget-- > 0) {
            BlockPos target = area.target(cursor);
            if (!level.isLoaded(target)) {
                waiting = QuarryStatus.BLOCKED;
                return;
            }
            BlockState targetState = level.getBlockState(target);
            if (!QuarryMiner.shouldMine(level, target, targetState)) {
                passOver();
                continue;
            }
            int required = headTier.ticksPerBlock();
            if (drillTicks < required) {
                if (!hasBufferRoom()) {
                    waiting = QuarryStatus.OUTPUT_FULL;
                    return;
                }
                if (!consumeFuelTick()) {
                    waiting = QuarryStatus.NO_FUEL;
                    return;
                }
                drillTicks++;
                showDrilling(level, target, targetState, required);
                if (drillTicks < required) return;
            }
            // Drilling complete: the server re-checks the block, the protections and the buffer.
            switch (QuarryMiner.mine(level, target, worldPosition, buffer)) {
                case OUTPUT_FULL -> waiting = QuarryStatus.OUTPUT_FULL;
                case MINED -> {
                    blocksMined++;
                    lifetimeBlocksMined++;
                    passOver();
                }
                case SKIPPED -> passOver();
            }
            return;
        }
    }

    private void passOver() {
        clearCracks();
        drillTicks = 0;
        advance();
    }

    /** A drilled block must leave at least one free slot, or merge into an existing stack. */
    private boolean hasBufferRoom() {
        for (int slot = 0; slot < buffer.getSlots(); slot++) {
            ItemStack stack = buffer.getStackInSlot(slot);
            if (stack.isEmpty() || stack.getCount() < stack.getMaxStackSize()) return true;
        }
        return false;
    }

    private void showDrilling(ServerLevel level, BlockPos target, BlockState targetState, int required) {
        int stage = Math.min(9, drillTicks * 10 / required);
        if (stage != crackStage) {
            crackStage = stage;
            crackPos = target;
            level.destroyBlockProgress(crackId(), target, stage);
        }
        if (drillTicks % 10 == 1) {
            var sound = targetState.getSoundType(level, target, null);
            level.playSound(null, target, sound.getHitSound(), SoundSource.BLOCKS, sound.getVolume() * 0.5F, sound.getPitch() * 0.8F);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, targetState), target.getX() + 0.5, target.getY() + 1.0,
                    target.getZ() + 0.5, 4, 0.25, 0.05, 0.25, 0.05);
        }
    }

    private void clearCracks() {
        if (crackPos != null && level != null) level.destroyBlockProgress(crackId(), crackPos, -1);
        crackPos = null;
        crackStage = -1;
    }

    private int crackId() {
        return 0x51A2_0000 ^ worldPosition.hashCode();
    }

    // ---- Client synchronisation: only the few values the renderer needs, never the area blocks ----

    private void sync() {
        if (level == null || level.isClientSide) return;
        syncedCursor = cursor;
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        if (cornerA != null) tag.put("corner_a", NbtUtils.writeBlockPos(cornerA));
        if (cornerB != null) tag.put("corner_b", NbtUtils.writeBlockPos(cornerB));
        if (stopY != null) tag.putInt("stop_y", stopY);
        tag.putLong("cursor", cursor);
        tag.putInt("status", status.ordinal());
        tag.putInt("drill", drillTicks);
        tag.putInt("ticks_per_block", ticksPerBlock());
        tag.putInt("head", miningHead().map(MiningHeadTier::level).orElse(0));
        tag.putInt("area_check", checkArea().ordinal());
        tag.putString("name", customName);
        tag.putString("network", homeNetwork == null ? "" : homeNetworkName);
        tag.putBoolean("has_network", homeNetwork != null);
        tag.putBoolean("finished", finished);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        readClientData(tag);
    }

    @Override
    public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet, HolderLookup.Provider registries) {
        readClientData(packet.getTag());
    }

    private void readClientData(CompoundTag tag) {
        cornerA = NbtUtils.readBlockPos(tag, "corner_a").orElse(null);
        cornerB = NbtUtils.readBlockPos(tag, "corner_b").orElse(null);
        stopY = tag.contains("stop_y") ? tag.getInt("stop_y") : null;
        cursor = tag.getLong("cursor");
        status = QuarryStatus.byId(tag.getInt("status"));
        drillTicks = tag.getInt("drill");
        clientTicksPerBlock = tag.getInt("ticks_per_block");
        clientHeadLevel = tag.getInt("head");
        customName = tag.getString("name");
        homeNetworkName = tag.getString("network");
        clientHasNetwork = tag.getBoolean("has_network");
        int check = tag.getInt("area_check");
        clientAreaCheck = check >= 0 && check < AreaCheck.values().length ? AreaCheck.values()[check] : AreaCheck.MISSING_CORNERS;
        finished = tag.getBoolean("finished");
    }

    // ---- Lifecycle and persistence --------------------------------------------------------------

    /** Drops every stored item when the controller is broken; nothing is deleted. */
    public void dropContents(Level level, BlockPos pos) {
        for (ItemStackHandler handler : new ItemStackHandler[]{head, fuel, buffer}) {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), handler.getStackInSlot(slot).copy());
                handler.setStackInSlot(slot, ItemStack.EMPTY);
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("head", head.serializeNBT(registries));
        tag.put("fuel", fuel.serializeNBT(registries));
        tag.put("buffer", buffer.serializeNBT(registries));
        tag.putInt("fuel_ticks", fuelTicks);
        if (cornerA != null) tag.put("corner_a", NbtUtils.writeBlockPos(cornerA));
        if (cornerB != null) tag.put("corner_b", NbtUtils.writeBlockPos(cornerB));
        if (stopY != null) tag.putInt("stop_y", stopY);
        tag.putLong("cursor", cursor);
        tag.putLong("blocks_mined", blocksMined);
        tag.putLong("lifetime_blocks_mined", lifetimeBlocksMined);
        tag.putBoolean("running", running);
        tag.putBoolean("paused", paused);
        tag.putBoolean("finished", finished);
        tag.putInt("drill_ticks", drillTicks);
        if (!customName.isEmpty()) tag.putString("name", customName);
        tag.putUUID("device_id", deviceId);
        if (owner != null) tag.putUUID("owner", owner);
        tag.putString("owner_name", ownerName);
        if (homeNetwork != null) {
            tag.putUUID("home_network", homeNetwork);
            tag.putString("home_network_name", homeNetworkName);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("head")) head.deserializeNBT(registries, tag.getCompound("head"));
        if (tag.contains("fuel")) fuel.deserializeNBT(registries, tag.getCompound("fuel"));
        if (tag.contains("buffer")) buffer.deserializeNBT(registries, tag.getCompound("buffer"));
        fuelTicks = Math.max(0, tag.getInt("fuel_ticks"));
        cornerA = NbtUtils.readBlockPos(tag, "corner_a").orElse(null);
        cornerB = NbtUtils.readBlockPos(tag, "corner_b").orElse(null);
        stopY = tag.contains("stop_y") ? tag.getInt("stop_y") : null;
        cursor = Math.max(0, tag.getLong("cursor"));
        blocksMined = Math.max(0, tag.getLong("blocks_mined"));
        lifetimeBlocksMined = Math.max(0, tag.getLong("lifetime_blocks_mined"));
        running = tag.getBoolean("running");
        paused = tag.getBoolean("paused");
        finished = tag.getBoolean("finished");
        drillTicks = Math.max(0, tag.getInt("drill_ticks"));
        customName = tag.getString("name");
        if (tag.hasUUID("device_id")) deviceId = tag.getUUID("device_id");
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        ownerName = tag.getString("owner_name");
        homeNetwork = tag.hasUUID("home_network") ? tag.getUUID("home_network") : null;
        homeNetworkName = homeNetwork == null ? "" : tag.getString("home_network_name");
    }

    @Override
    public void setRemoved() {
        clearCracks();
        releaseDevice();
        super.setRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        releaseDevice();
        super.onChunkUnloaded();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        portDirty = true;
        status = computeStatus();
    }
}
