package fr.lkdm.homelink.quarry.blockentity;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * Standard item access for hoppers, pipes and comparators: the buffer, extraction only. Nothing can be
 * inserted (the quarry runs on HomeLink Energy, not fuel) and the Mining Head slot is never exposed.
 */
final class QuarryAutomationHandler implements IItemHandler {
    private final ItemStackHandler buffer;

    QuarryAutomationHandler(ItemStackHandler buffer) {
        this.buffer = buffer;
    }

    @Override
    public int getSlots() {
        return buffer.getSlots();
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return buffer.getStackInSlot(slot);
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        return stack;
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        return buffer.extractItem(slot, amount, simulate);
    }

    @Override
    public int getSlotLimit(int slot) {
        return buffer.getSlotLimit(slot);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return false;
    }
}
