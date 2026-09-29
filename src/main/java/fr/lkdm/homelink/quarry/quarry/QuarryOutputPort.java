package fr.lkdm.homelink.quarry.quarry;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * OUTPUT port on the back of the controller. A compatible INPUT or BOTH HomeCore item port is
 * placed directly behind (for example the HomeLink Storage Deposit). It is reached through the face it shows to the quarry,
 * so its own face rules apply; a plain chest behind the quarry receives nothing.
 */
public final class QuarryOutputPort {
    private static final Logger LOGGER = LogUtils.getLogger();

    private QuarryOutputPort() {
    }

    /** Side of the controller that carries the port: the opposite of its front. */
    public static Direction portSide(Direction facing) {
        return facing.getOpposite();
    }

    /** The compatible input behind the controller, or null. Never loads a chunk. */
    @Nullable
    public static IItemHandler input(ServerLevel level, BlockPos controller, Direction facing) {
        Direction back = portSide(facing);
        BlockPos pos = controller.relative(back);
        if (!level.isLoaded(pos)) return null;
        var input = level.getCapability(fr.lkdm.homecore.api.item.ItemApi.BLOCK, pos, back.getOpposite());
        return input != null && input.type().canReceive() ? input : null;
    }

    /**
     * Moves at most one stack from the buffer. The amount is first simulated, then inserted, and only
     * the amount really accepted is removed from the buffer: nothing is lost or duplicated.
     *
     * @return number of items moved
     */
    public static int transferOneStack(ItemStackHandler buffer, IItemHandler target) {
        for (int slot = 0; slot < buffer.getSlots(); slot++) {
            ItemStack stack = buffer.getStackInSlot(slot);
            if (stack.isEmpty()) continue;
            int accepted = stack.getCount() - insert(target, stack.copy(), true).getCount();
            if (accepted <= 0) continue;
            ItemStack rest = insert(target, stack.copyWithCount(accepted), false);
            int moved = accepted - rest.getCount();
            if (!rest.isEmpty()) LOGGER.debug("ITEM_INPUT accepted {} of the {} it announced", moved, accepted);
            if (moved > 0) buffer.extractItem(slot, moved, false);
            return moved;
        }
        return 0;
    }

    /** Inserts across the target's slots, merging first, like a hopper would. */
    private static ItemStack insert(IItemHandler target, ItemStack stack, boolean simulate) {
        return net.neoforged.neoforge.items.ItemHandlerHelper.insertItemStacked(target, stack, simulate);
    }
}
