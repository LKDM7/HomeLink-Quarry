package fr.lkdm.homelink.quarry.menu;

import fr.lkdm.homelink.quarry.blockentity.QuarryControllerBlockEntity;
import fr.lkdm.homelink.quarry.item.MiningHeadItem;
import fr.lkdm.homelink.quarry.quarry.AreaCheck;
import fr.lkdm.homelink.quarry.quarry.QuarryArea;
import fr.lkdm.homelink.quarry.quarry.QuarryFuel;
import fr.lkdm.homelink.quarry.quarry.QuarryStatus;
import fr.lkdm.homelink.quarry.registry.QuarryRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Quarry Controller menu: Mining Head slot, fuel slot, the 27-slot buffer (take only) and the numbers
 * shown by the screen. Buttons use the vanilla container button packet and are validated here.
 */
public class QuarryMenu extends AbstractContainerMenu {
    public static final int BUTTON_START = 0;
    public static final int BUTTON_PAUSE = 1;
    public static final int BUTTON_RESUME = 2;
    public static final int BUTTON_STOP = 3;
    public static final int BUTTON_STOP_Y_DOWN = 10;
    public static final int BUTTON_STOP_Y_UP = 11;
    public static final int BUTTON_STOP_Y_DOWN_10 = 12;
    public static final int BUTTON_STOP_Y_UP_10 = 13;
    public static final int BUTTON_STOP_Y_MIN = 14;

    /** Slot positions of the OUTPUT view, centred like the HomeLink Farm station. */
    public static final int HEAD_X = 55;
    public static final int HEAD_Y = 30;
    public static final int FUEL_X = 163;
    public static final int FUEL_Y = 30;
    public static final int BUFFER_X = 55;
    public static final int BUFFER_Y = 62;
    public static final int INVENTORY_X = 55;
    public static final int INVENTORY_Y = 130;
    public static final int HOTBAR_Y = 188;

    /** Synced values; 32-bit numbers use two 16-bit data slots. */
    enum Field {
        STATUS, WIDTH, LENGTH, START_Y, STOP_Y, LAYER_Y, PROGRESS, MINED_LO, MINED_HI, REMAINING_LO, REMAINING_HI,
        HEAD, FUEL_PERCENT, RUNTIME_LO, RUNTIME_HI, ESTIMATE_LO, ESTIMATE_HI, BUFFER_USED, AREA_CHECK, HAS_AREA,
        LOCKED, MAX_SIDE, OUTPUT_CONNECTED, BUFFER_PERCENT
    }

    private static final int SLOTS = 2 + QuarryControllerBlockEntity.BUFFER_SLOTS;

    private final BlockPos pos;
    @Nullable private final QuarryControllerBlockEntity entity;
    private final ContainerData data;
    private final boolean clientSide;
    private boolean slotsVisible;

    /** Client constructor. */
    public QuarryMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, buffer.readBlockPos(), null, new ItemStackHandler(1), new ItemStackHandler(1),
                new ItemStackHandler(QuarryControllerBlockEntity.BUFFER_SLOTS), new SimpleContainerData(Field.values().length));
    }

    /** Server constructor. */
    public QuarryMenu(int id, Inventory inventory, QuarryControllerBlockEntity entity) {
        this(id, inventory, entity.getBlockPos(), entity, entity.headSlot(), entity.fuelSlot(), entity.buffer(), new ServerData(entity));
    }

    private QuarryMenu(int id, Inventory inventory, BlockPos pos, @Nullable QuarryControllerBlockEntity entity,
                       IItemHandler head, IItemHandler fuel, IItemHandler buffer, ContainerData data) {
        super(QuarryRegistries.QUARRY_MENU.get(), id);
        this.pos = pos;
        this.entity = entity;
        this.data = data;
        this.clientSide = entity == null;
        addSlot(new ViewSlot(head, 0, HEAD_X, HEAD_Y));
        addSlot(new ViewSlot(fuel, 0, FUEL_X, FUEL_Y));
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new ViewSlot(buffer, column + row * 9, BUFFER_X + column * 18, BUFFER_Y + row * 18) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return false;
                    }
                });
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new PlayerSlot(inventory, column + row * 9 + 9, INVENTORY_X + column * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) addSlot(new PlayerSlot(inventory, column, INVENTORY_X + column * 18, HOTBAR_Y));
        addDataSlots(data);
    }

    /** Client: shows or hides the slots (the screen's OUTPUT view). The server keeps them usable. */
    public void setSlotsVisible(boolean visible) {
        slotsVisible = visible;
    }

    private boolean slotActive() {
        return !clientSide || slotsVisible;
    }

    private class ViewSlot extends SlotItemHandler {
        ViewSlot(IItemHandler handler, int index, int x, int y) {
            super(handler, index, x, y);
        }

        @Override
        public boolean isActive() {
            return slotActive();
        }
    }

    private class PlayerSlot extends Slot {
        PlayerSlot(Inventory inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }

        @Override
        public boolean isActive() {
            return slotActive();
        }
    }

    public BlockPos position() {
        return pos;
    }

    @Override
    public boolean stillValid(Player player) {
        if (entity == null) return true;
        return !entity.isRemoved() && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (entity == null || !stillValid(player) || !entity.mayControl(player)) return false;
        int stopY = entity.area().map(QuarryArea::stopY).orElse(0);
        // Depth is configuration: it needs the configure right, not only control.
        if (id >= BUTTON_STOP_Y_DOWN && !entity.mayConfigure(player)) return false;
        return switch (id) {
            case BUTTON_START -> entity.start();
            case BUTTON_PAUSE -> entity.pause();
            case BUTTON_RESUME -> entity.resume();
            case BUTTON_STOP -> entity.stop();
            case BUTTON_STOP_Y_DOWN -> entity.setStopY(stopY - 1);
            case BUTTON_STOP_Y_UP -> entity.setStopY(stopY + 1);
            case BUTTON_STOP_Y_DOWN_10 -> entity.setStopY(stopY - 10);
            case BUTTON_STOP_Y_UP_10 -> entity.setStopY(stopY + 10);
            case BUTTON_STOP_Y_MIN -> entity.setStopY(Integer.MIN_VALUE);
            default -> false;
        };
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < SLOTS) {
            if (!moveItemStackTo(stack, SLOTS, slots.size(), true)) return ItemStack.EMPTY;
        } else if (stack.getItem() instanceof MiningHeadItem) {
            if (!moveItemStackTo(stack, 0, 1, false)) return ItemStack.EMPTY;
        } else if (QuarryFuel.isFuel(stack)) {
            if (!moveItemStackTo(stack, 1, 2, false)) return ItemStack.EMPTY;
        } else {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        slot.onTake(player, stack);
        return original;
    }

    // ---- Values for the screen ------------------------------------------------------------------

    private int get(Field field) {
        return data.get(field.ordinal());
    }

    private long wide(Field low) {
        return (get(low) & 0xFFFFL) | ((long) (get(Field.values()[low.ordinal() + 1]) & 0xFFFF) << 16);
    }

    public QuarryStatus status() { return QuarryStatus.byId(get(Field.STATUS)); }
    public int width() { return get(Field.WIDTH); }
    public int length() { return get(Field.LENGTH); }
    public int startY() { return (short) get(Field.START_Y); }
    public int stopY() { return (short) get(Field.STOP_Y); }
    public int layerY() { return (short) get(Field.LAYER_Y); }
    /** Progress in [0, 1]. */
    public double progress() { return get(Field.PROGRESS) / 10000.0; }
    public long blocksMined() { return wide(Field.MINED_LO); }
    public long positionsRemaining() { return wide(Field.REMAINING_LO); }
    public int headLevel() { return get(Field.HEAD); }
    public int fuelPercent() { return get(Field.FUEL_PERCENT); }
    public long runtimeSeconds() { return wide(Field.RUNTIME_LO); }
    public long estimatedBlocks() { return wide(Field.ESTIMATE_LO); }
    public int bufferUsed() { return get(Field.BUFFER_USED); }
    public int bufferPercent() { return get(Field.BUFFER_PERCENT); }
    public AreaCheck areaCheck() {
        int id = get(Field.AREA_CHECK);
        return id >= 0 && id < AreaCheck.values().length ? AreaCheck.values()[id] : AreaCheck.MISSING_CORNERS;
    }
    public boolean hasArea() { return get(Field.HAS_AREA) != 0; }
    public boolean locked() { return get(Field.LOCKED) != 0; }
    public int maxSide() { return get(Field.MAX_SIDE); }
    public boolean outputConnected() { return get(Field.OUTPUT_CONNECTED) != 0; }

    /** Live values read from the block entity on the server. */
    private static final class ServerData implements ContainerData {
        private final QuarryControllerBlockEntity entity;

        ServerData(QuarryControllerBlockEntity entity) {
            this.entity = entity;
        }

        @Override
        public int get(int index) {
            QuarryArea area = entity.area().orElse(null);
            return switch (Field.values()[index]) {
                case STATUS -> entity.status().ordinal();
                case WIDTH -> area == null ? 0 : area.width();
                case LENGTH -> area == null ? 0 : area.length();
                case START_Y -> area == null ? 0 : area.startY();
                case STOP_Y -> area == null ? 0 : area.stopY();
                case LAYER_Y -> area == null ? 0 : area.layerY(Math.min(entity.cursor(), Math.max(0, area.totalPositions() - 1)));
                case PROGRESS -> (int) Math.round(entity.progress() * 10000);
                case MINED_LO -> low(entity.blocksMined());
                case MINED_HI -> high(entity.blocksMined());
                case REMAINING_LO -> low(entity.positionsRemaining());
                case REMAINING_HI -> high(entity.positionsRemaining());
                case HEAD -> entity.headLevel();
                case FUEL_PERCENT -> entity.fuelPercent();
                case RUNTIME_LO -> low(entity.runtimeTicks() / 20);
                case RUNTIME_HI -> high(entity.runtimeTicks() / 20);
                case ESTIMATE_LO -> low(entity.estimatedBlocks());
                case ESTIMATE_HI -> high(entity.estimatedBlocks());
                case BUFFER_USED -> entity.bufferUsedSlots();
                case BUFFER_PERCENT -> entity.bufferPercent();
                case AREA_CHECK -> entity.checkArea().ordinal();
                case HAS_AREA -> area == null ? 0 : 1;
                case LOCKED -> entity.configurationLocked() ? 1 : 0;
                case MAX_SIDE -> entity.tier().maxSide();
                case OUTPUT_CONNECTED -> entity.outputConnected() ? 1 : 0;
            };
        }

        private static int low(long value) {
            return (int) (Math.min(value, 0xFFFF_FFFFL) & 0xFFFF);
        }

        private static int high(long value) {
            return (int) ((Math.min(value, 0xFFFF_FFFFL) >>> 16) & 0xFFFF);
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int getCount() {
            return Field.values().length;
        }
    }
}
