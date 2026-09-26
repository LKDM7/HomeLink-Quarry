package fr.lkdm.homelink.quarry.blockentity;

import fr.lkdm.homelink.quarry.quarry.QuarryFuel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * Standard item access for hoppers, pipes and comparators. Slot 0 is the fuel slot: fuel can be inserted
 * and only spent containers (empty buckets) extracted. Slots 1..n are the buffer: extraction only.
 * The Mining Head slot is never exposed.
 */
final class QuarryAutomationHandler implements IItemHandler {
    private final ItemStackHandler fuel;
    private final ItemStackHandler buffer;

    QuarryAutomationHandler(ItemStackHandler fuel, ItemStackHandler buffer) {
        this.fuel = fuel;
        this.buffer = buffer;
    }

    @Override
    public int getSlots() {
        return 1 + buffer.getSlots();
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return slot == 0 ? fuel.getStackInSlot(0) : buffer.getStackInSlot(slot - 1);
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (slot != 0 || !QuarryFuel.isFuel(stack)) return stack;
        return fuel.insertItem(0, stack, simulate);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (slot == 0) return QuarryFuel.isFuel(fuel.getStackInSlot(0)) ? ItemStack.EMPTY : fuel.extractItem(0, amount, simulate);
        return buffer.extractItem(slot - 1, amount, simulate);
    }

    @Override
    public int getSlotLimit(int slot) {
        return slot == 0 ? fuel.getSlotLimit(0) : buffer.getSlotLimit(slot - 1);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return slot == 0 && QuarryFuel.isFuel(stack);
    }
}
