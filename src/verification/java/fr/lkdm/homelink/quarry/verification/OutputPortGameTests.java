package fr.lkdm.homelink.quarry.verification;

import static fr.lkdm.homelink.quarry.verification.FoundationGameTests.check;
import static fr.lkdm.homelink.quarry.verification.MiningGameTests.count;

import fr.lkdm.homelink.quarry.block.QuarryControllerBlock;
import fr.lkdm.homelink.quarry.blockentity.QuarryControllerBlockEntity;
import fr.lkdm.homelink.quarry.quarry.QuarryStatus;
import fr.lkdm.homelink.quarry.registry.QuarryRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Prompt 2A: ITEM_OUTPUT port, automatic connection and the transactional buffer transfer.
 * HomeLink Storage is not loaded here: the validation data pack adds the vanilla dropper to
 * {@code homelink_quarry:item_inputs} to stand in for the Storage Deposit.
 */
@GameTestHolder(QuarryValidation.MOD_ID)
@PrefixGameTestTemplate(false)
public final class OutputPortGameTests {
    /** Controller facing NORTH: the port, and so the ITEM_INPUT, is on its SOUTH side. */
    private static final BlockPos QUARRY = new BlockPos(2, 1, 2);
    private static final BlockPos BEHIND = QUARRY.south();

    private static QuarryControllerBlockEntity quarry(GameTestHelper helper) {
        helper.setBlock(QUARRY, QuarryRegistries.QUARRY_II.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
        return helper.getBlockEntity(QUARRY);
    }

    private static int port(GameTestHelper helper) {
        return helper.getBlockState(QUARRY).getValue(QuarryControllerBlock.PORT);
    }

    private static int total(Container container, Item item) {
        return container.countItem(item);
    }

    @GameTest(template = "empty", batch = "port_a", timeoutTicks = 200)
    public static void inputBehindReceivesTheBuffer(GameTestHelper helper) {
        QuarryControllerBlockEntity entity = quarry(helper);
        entity.buffer().setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 64));
        entity.buffer().setStackInSlot(5, new ItemStack(Items.RAW_IRON, 7));
        helper.setBlock(BEHIND, Blocks.DROPPER);
        helper.runAtTickTime(4, () -> check(helper, port(helper) == 1, "Port did not light up first"));
        helper.succeedWhen(() -> {
            check(helper, port(helper) == 2 && entity.outputConnected(), "Not CONNECTED");
            Container input = helper.getBlockEntity(BEHIND);
            check(helper, entity.bufferUsedSlots() == 0, "Buffer not emptied: " + entity.bufferUsedSlots());
            check(helper, total(input, Items.COBBLESTONE) == 64 && total(input, Items.RAW_IRON) == 7, "Items lost or duplicated");
        });
    }

    @GameTest(template = "empty", batch = "port_b", timeoutTicks = 120)
    public static void noInputKeepsTheBuffer(GameTestHelper helper) {
        QuarryControllerBlockEntity entity = quarry(helper);
        entity.buffer().setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 64));
        helper.setBlock(BEHIND, Blocks.CHEST);
        helper.setBlock(QUARRY.north(), Blocks.DROPPER);
        helper.runAtTickTime(100, () -> {
            check(helper, port(helper) == 0 && !entity.outputConnected(), "A chest behind or an input in front connected");
            check(helper, count(entity, Items.COBBLESTONE) == 64, "Buffer changed without an ITEM_INPUT");
            check(helper, ((Container) helper.getBlockEntity(BEHIND)).isEmpty(), "A plain chest received items");
            check(helper, ((Container) helper.getBlockEntity(QUARRY.north())).isEmpty(), "The front is not a port");
            helper.succeed();
        });
    }

    /** One stack per second at most, never the whole buffer in one tick. */
    @GameTest(template = "empty", batch = "port_c", timeoutTicks = 200)
    public static void transferIsPaced(GameTestHelper helper) {
        QuarryControllerBlockEntity entity = quarry(helper);
        for (int slot = 0; slot < 6; slot++) entity.buffer().setStackInSlot(slot, new ItemStack(Items.DIRT, 8));
        helper.setBlock(BEHIND, Blocks.DROPPER);
        int[] seen = new int[1];
        helper.runAtTickTime(40, () -> seen[0] = entity.bufferUsedSlots());
        helper.runAtTickTime(59, () -> check(helper, seen[0] - entity.bufferUsedSlots() <= 1, "More than one stack in one second"));
        helper.succeedWhen(() -> check(helper, entity.bufferUsedSlots() == 0, "Buffer not emptied"));
    }

    /** A partly full input takes what fits; the rest stays in the buffer, the totals never change. */
    @GameTest(template = "empty", batch = "port_d", timeoutTicks = 160)
    public static void partialTransferConservesItems(GameTestHelper helper) {
        QuarryControllerBlockEntity entity = quarry(helper);
        helper.setBlock(BEHIND, Blocks.DROPPER);
        Container input = helper.getBlockEntity(BEHIND);
        for (int slot = 0; slot < 9; slot++) input.setItem(slot, new ItemStack(Items.DIRT, 64));
        input.setItem(4, new ItemStack(Items.COBBLESTONE, 60));
        entity.buffer().setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 64));
        helper.runAtTickTime(140, () -> {
            check(helper, total(input, Items.COBBLESTONE) == 64, "Input did not take the 4 that fit");
            check(helper, count(entity, Items.COBBLESTONE) == 60, "Buffer did not keep the 60 that did not fit");
            check(helper, total(input, Items.DIRT) == 8 * 64, "Input content changed");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "port_e", timeoutTicks = 200)
    public static void disconnectAndReconnect(GameTestHelper helper) {
        QuarryControllerBlockEntity entity = quarry(helper);
        helper.setBlock(BEHIND, Blocks.DROPPER);
        helper.runAtTickTime(30, () -> {
            check(helper, entity.outputConnected(), "Not connected");
            helper.setBlock(BEHIND, Blocks.AIR);
            entity.buffer().setStackInSlot(0, new ItemStack(Items.GOLD_INGOT, 5));
        });
        helper.runAtTickTime(40, () -> {
            check(helper, port(helper) == 0 && !entity.outputConnected(), "Still connected after removal");
            check(helper, count(entity, Items.GOLD_INGOT) == 5, "Items lost on disconnection");
            helper.setBlock(BEHIND, Blocks.DROPPER);
        });
        helper.runAtTickTime(80, () -> {
            check(helper, entity.outputConnected(), "No reconnection");
            helper.succeedWhen(() -> check(helper, total(helper.getBlockEntity(BEHIND), Items.GOLD_INGOT) == 5, "Not delivered after reconnection"));
        });
    }

    /** A quarry stopped by OUTPUT_FULL resumes once the input frees room; a player PAUSE is never lifted. */
    @GameTest(template = "empty", batch = "port_f", timeoutTicks = 300)
    public static void outputFullResumesButPauseStays(GameTestHelper helper) {
        QuarryControllerBlockEntity entity = quarry(helper);
        BlockPos target = new BlockPos(4, 1, 2);
        BlockPos last = new BlockPos(7, 1, 2);
        for (int x = 4; x <= 7; x++) helper.setBlock(new BlockPos(x, 1, 2), Blocks.STONE);
        entity.setCorners(helper.absolutePos(target), helper.absolutePos(last));
        entity.setStopY(helper.absolutePos(target).getY());
        entity.headSlot().setStackInSlot(0, new ItemStack(QuarryRegistries.MINING_HEAD_III.get()));
        entity.energyPort().insert(1_000, false);
        for (int slot = 0; slot < QuarryControllerBlockEntity.BUFFER_SLOTS; slot++) entity.buffer().setStackInSlot(slot, new ItemStack(Items.DIRT, 64));
        check(helper, entity.start(), "Start");
        helper.runAtTickTime(10, () -> {
            check(helper, entity.status() == QuarryStatus.OUTPUT_FULL, "Expected OUTPUT_FULL, got " + entity.status());
            helper.setBlock(BEHIND, Blocks.DROPPER);
        });
        helper.runAtTickTime(150, () -> {
            helper.assertBlockPresent(Blocks.AIR, target);
            check(helper, count(entity, Items.COBBLESTONE) + total(helper.getBlockEntity(BEHIND), Items.COBBLESTONE) == entity.blocksMined()
                    && entity.blocksMined() >= 1, "Mining did not resume after the input made room");
            check(helper, entity.pause() && entity.status() == QuarryStatus.PAUSED, "Pause refused: " + entity.status());
        });
        helper.runAtTickTime(280, () -> {
            check(helper, entity.status() == QuarryStatus.PAUSED, "A player pause was lifted automatically");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "port_g", timeoutTicks = 100)
    public static void comparatorAndHopper(GameTestHelper helper) {
        QuarryControllerBlockEntity entity = quarry(helper);
        var level = helper.getLevel();
        var pos = helper.absolutePos(QUARRY);
        check(helper, helper.getBlockState(QUARRY).getAnalogOutputSignal(level, pos) == 0, "Empty buffer must give 0");
        for (int slot = 0; slot < QuarryControllerBlockEntity.BUFFER_SLOTS; slot++) entity.buffer().setStackInSlot(slot, new ItemStack(Items.DIRT, 64));
        check(helper, helper.getBlockState(QUARRY).getAnalogOutputSignal(level, pos) == 15, "Full buffer must give 15");
        for (int slot = 1; slot < QuarryControllerBlockEntity.BUFFER_SLOTS; slot++) entity.buffer().setStackInSlot(slot, ItemStack.EMPTY);
        int one = helper.getBlockState(QUARRY).getAnalogOutputSignal(level, pos);
        check(helper, one > 0 && one < 15, "Partial buffer signal");
        helper.setBlock(QUARRY.below(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
        helper.succeedWhen(() -> check(helper, total(helper.getBlockEntity(QUARRY.below()), Items.DIRT) == 5
                || count(entity, Items.DIRT) < 64, "A hopper below did not extract from the buffer"));
    }
}
