package fr.lkdm.homelink.quarry.quarry;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import fr.lkdm.homelink.quarry.HomeLinkQuarry;
import fr.lkdm.homelink.quarry.block.QuarryControllerBlock;
import fr.lkdm.homelink.quarry.config.QuarryConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.slf4j.Logger;

/**
 * Server-side block breaking. Drops are computed with the vanilla loot tables, checked against a copy
 * of the buffer and only then is the block removed, so nothing is duplicated or silently deleted.
 */
public final class QuarryMiner {
    /** Data pack extension of the configurable blacklist. */
    public static final TagKey<Block> BLACKLIST_TAG = TagKey.create(Registries.BLOCK, HomeLinkQuarry.id("quarry_blacklist"));
    private static final GameProfile PROFILE = new GameProfile(UUID.fromString("3f0b5d4e-8c21-4a47-9b8e-2d6f1c7a9e10"), "[HomeLink Quarry]");
    private static final Logger LOGGER = LogUtils.getLogger();

    public enum Result {
        MINED,
        /** The block stays in place and the position is passed over. */
        SKIPPED,
        /** Nothing changed; retry once the buffer has room. */
        OUTPUT_FULL
    }

    private QuarryMiner() {
    }

    /** Plain pickaxe used for loot: harvests ores, no Silk Touch or Fortune. */
    private static ItemStack tool() {
        return new ItemStack(Items.NETHERITE_PICKAXE);
    }

    public static FakePlayer operator(ServerLevel level, BlockPos controller) {
        FakePlayer player = FakePlayerFactory.get(level, PROFILE);
        player.setPos(controller.getX() + 0.5, controller.getY() + 0.5, controller.getZ() + 0.5);
        return player;
    }

    /** True when the quarry should drill this block; false to pass over it without spending time. */
    public static boolean shouldMine(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.isAir()) return false;
        if (state.getBlock() instanceof LiquidBlock) return false;
        if (state.getBlock() instanceof QuarryControllerBlock) return false;
        if (state.getDestroySpeed(level, pos) < 0) return false;
        if (state.is(BLACKLIST_TAG) || QuarryConfig.blacklist().contains(state.getBlock())) return false;
        if (!QuarryConfig.MINE_CONTAINERS.get() && holdsItems(level, pos, level.getBlockEntity(pos))) return false;
        return true;
    }

    private static boolean holdsItems(ServerLevel level, BlockPos pos, BlockEntity entity) {
        return entity instanceof Container || (entity != null && level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null) != null);
    }

    /** Breaks one block into the buffer, or leaves everything unchanged. */
    public static Result mine(ServerLevel level, BlockPos pos, BlockPos controller, ItemStackHandler buffer) {
        BlockState state = level.getBlockState(pos);
        if (!shouldMine(level, pos, state)) return Result.SKIPPED;
        FakePlayer operator = operator(level, controller);
        if (NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, state, operator)).isCanceled()) return Result.SKIPPED;

        BlockEntity entity = level.getBlockEntity(pos);
        List<ItemStack> items = new ArrayList<>(Block.getDrops(state, level, pos, entity, operator, tool()));
        // Shulker-like drops carry their contents in the item itself; other inventories are emptied into the buffer.
        boolean embedded = items.stream().anyMatch(stack -> stack.has(DataComponents.CONTAINER));
        IItemHandler handler = entity == null || embedded ? null : level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
        Container container = !embedded && entity instanceof Container c ? c : null;
        List<ItemStack> contents = new ArrayList<>();
        if (container != null) {
            for (int slot = 0; slot < container.getContainerSize(); slot++)
                if (!container.getItem(slot).isEmpty()) contents.add(container.getItem(slot).copy());
        } else if (handler != null) {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack extractable = handler.extractItem(slot, handler.getStackInSlot(slot).getCount(), true);
                if (!extractable.isEmpty()) contents.add(extractable);
            }
        }
        List<ItemStack> all = new ArrayList<>(items);
        all.addAll(contents);
        if (!fits(new ItemStackHandler(buffer.getSlots()), all)) {
            LOGGER.debug("Quarry at {} leaves {} in place: its content exceeds an empty buffer", controller, pos);
            return Result.SKIPPED;
        }
        if (!fits(copy(buffer), all)) return Result.OUTPUT_FULL;

        // Take the inventory content exactly once, then remove the block without vanilla drops.
        List<ItemStack> taken = new ArrayList<>(items);
        if (container != null) {
            taken.addAll(contents);
            container.clearContent();
        } else if (handler != null) {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack extracted = handler.extractItem(slot, handler.getStackInSlot(slot).getCount(), false);
                if (!extracted.isEmpty()) taken.add(extracted);
            }
        }
        level.destroyBlock(pos, false, operator);
        for (ItemStack stack : taken) store(level, controller, buffer, stack);
        // Items some blocks spill by themselves on removal are collected too, when they fit.
        for (ItemEntity spilled : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(0.5), item -> item.tickCount == 0)) {
            ItemStack rest = ItemHandlerHelper.insertItemStacked(buffer, spilled.getItem().copy(), false);
            if (rest.isEmpty()) spilled.discard();
            else spilled.setItem(rest);
        }
        return Result.MINED;
    }

    /** Inserts into the buffer; anything that could not fit is dropped at the controller, never deleted. */
    private static void store(ServerLevel level, BlockPos controller, ItemStackHandler buffer, ItemStack stack) {
        ItemStack rest = ItemHandlerHelper.insertItemStacked(buffer, stack, false);
        if (!rest.isEmpty()) {
            LOGGER.warn("Quarry at {} could not buffer {}; dropping it at the controller", controller, rest);
            Block.popResource(level, controller.above(), rest);
        }
    }

    public static boolean fits(ItemStackHandler target, List<ItemStack> stacks) {
        for (ItemStack stack : stacks) {
            if (!ItemHandlerHelper.insertItemStacked(target, stack.copy(), false).isEmpty()) return false;
        }
        return true;
    }

    private static ItemStackHandler copy(ItemStackHandler source) {
        ItemStackHandler copy = new ItemStackHandler(source.getSlots());
        for (int slot = 0; slot < source.getSlots(); slot++) copy.setStackInSlot(slot, source.getStackInSlot(slot).copy());
        return copy;
    }
}
