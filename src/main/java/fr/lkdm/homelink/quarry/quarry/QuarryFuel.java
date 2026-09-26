package fr.lkdm.homelink.quarry.quarry;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;

/**
 * Fuel rules. Fuel is stored as running ticks using vanilla furnace burn times and is only
 * consumed while a head drills: it sets how long a quarry runs, never how fast it mines.
 */
public final class QuarryFuel {
    /** Internal tank size in running ticks: two coal blocks, or one lava bucket plus spare room. */
    public static final int CAPACITY = 32_000;

    private QuarryFuel() {
    }

    /** Vanilla (and mod-registered) smelting burn time of one item, 0 when it is not a fuel. */
    public static int burnTicks(ItemStack stack) {
        return stack.isEmpty() ? 0 : Math.max(0, stack.getBurnTime(RecipeType.SMELTING));
    }

    public static boolean isFuel(ItemStack stack) {
        return burnTicks(stack) > 0;
    }

    /** Blocks a head can drill with the given running ticks; the rate itself never depends on fuel. */
    public static long estimatedBlocks(long runtimeTicks, MiningHeadTier head) {
        return runtimeTicks / head.ticksPerBlock();
    }
}
