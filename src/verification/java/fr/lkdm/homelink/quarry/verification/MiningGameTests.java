package fr.lkdm.homelink.quarry.verification;

import static fr.lkdm.homelink.quarry.verification.FoundationGameTests.check;

import fr.lkdm.homelink.quarry.blockentity.QuarryControllerBlockEntity;
import fr.lkdm.homelink.quarry.quarry.QuarryStatus;
import fr.lkdm.homelink.quarry.registry.QuarryRegistries;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Phase 5: real ticks. Each test has its own batch so no neighbouring test lies inside a mined area.
 * Areas are placed next to the controller: controller at (0, 1, 0), area from (2, y, 0).
 */
@GameTestHolder(QuarryValidation.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MiningGameTests {
    /** Each head drills exactly its time: the block is still there just before, gone just after. */
    @GameTest(template = "empty", batch = "mining_speed", timeoutTicks = 260)
    public static void headTimingIsExact(GameTestHelper helper) {
        List<Item> heads = List.of(QuarryRegistries.MINING_HEAD_I.get(), QuarryRegistries.MINING_HEAD_II.get(), QuarryRegistries.MINING_HEAD_III.get());
        int[] ticks = {200, 120, 60};
        for (int i = 0; i < 3; i++) {
            BlockPos controller = new BlockPos(0, 1, i * 3);
            BlockPos target = new BlockPos(2, 1, i * 3);
            helper.setBlock(target, Blocks.STONE);
            QuarryControllerBlockEntity entity = quarry(helper, controller, target, target, heads.get(i), new ItemStack(Items.COAL));
            check(helper, entity.start(), "Start failed");
            int required = ticks[i];
            helper.runAtTickTime(required - 3, () -> helper.assertBlockPresent(Blocks.STONE, target));
            helper.runAtTickTime(required + 3, () -> {
                helper.assertBlockPresent(Blocks.AIR, target);
                check(helper, count(entity, Items.COBBLESTONE) == 1, "Stone drop not buffered");
            });
        }
        helper.runAtTickTime(250, helper::succeed);
    }

    /** Layer by layer down to Stop Y, then FINISHED; the layer below Stop Y is untouched. */
    @GameTest(template = "empty", batch = "mining_layers", timeoutTicks = 600)
    public static void minesDownToStopY(GameTestHelper helper) {
        BlockPos a = new BlockPos(2, 2, 0);
        BlockPos b = new BlockPos(3, 2, 1);
        for (int y = 0; y <= 2; y++) for (int x = 2; x <= 3; x++) for (int z = 0; z <= 1; z++) helper.setBlock(new BlockPos(x, y, z), Blocks.DIRT);
        QuarryControllerBlockEntity entity = quarry(helper, new BlockPos(0, 1, 0), a, b, QuarryRegistries.MINING_HEAD_III.get(), new ItemStack(Items.COAL));
        entity.setStopY(helper.absolutePos(new BlockPos(0, 1, 0)).getY());
        check(helper, entity.start(), "Start failed");
        helper.runAtTickTime(60 * 4 + 5, () -> {
            check(helper, entity.area().orElseThrow().layerY(entity.cursor()) == helper.absolutePos(new BlockPos(0, 1, 0)).getY(),
                    "Top layer must be finished before the next one");
            helper.assertBlockPresent(Blocks.AIR, new BlockPos(3, 2, 1));
            helper.assertBlockPresent(Blocks.DIRT, new BlockPos(2, 1, 0));
        });
        helper.succeedWhen(() -> {
            check(helper, entity.status() == QuarryStatus.FINISHED, "Not finished");
            check(helper, entity.blocksMined() == 8 && count(entity, Items.DIRT) == 8, "Expected 8 dirt");
            for (int x = 2; x <= 3; x++) for (int z = 0; z <= 1; z++) helper.assertBlockPresent(Blocks.DIRT, new BlockPos(x, 0, z));
            check(helper, entity.progress() == 1.0, "Progress must end at 100%");
        });
    }

    /** Bedrock and air are passed over without drilling time; progress never exceeds 100%. */
    @GameTest(template = "empty", batch = "mining_bedrock", timeoutTicks = 200)
    public static void bedrockIsNeverMined(GameTestHelper helper) {
        BlockPos a = new BlockPos(2, 1, 0);
        BlockPos b = new BlockPos(4, 1, 0);
        helper.setBlock(a, Blocks.BEDROCK);
        helper.setBlock(new BlockPos(3, 1, 0), Blocks.AIR);
        helper.setBlock(b, Blocks.STONE);
        QuarryControllerBlockEntity entity = quarry(helper, new BlockPos(0, 1, 0), a, b, QuarryRegistries.MINING_HEAD_III.get(), new ItemStack(Items.COAL));
        entity.setStopY(helper.absolutePos(a).getY());
        check(helper, entity.start(), "Start failed");
        helper.succeedWhen(() -> {
            check(helper, entity.status() == QuarryStatus.FINISHED, "Not finished");
            helper.assertBlockPresent(Blocks.BEDROCK, a);
            check(helper, entity.blocksMined() == 1 && count(entity, Items.COBBLESTONE) == 1, "Only the stone is mined");
        });
    }

    /** A chest and its content go to the buffer exactly once; nothing lies on the ground. */
    @GameTest(template = "empty", batch = "mining_chest", timeoutTicks = 200)
    public static void chestContentIsKept(GameTestHelper helper) {
        BlockPos chest = new BlockPos(2, 1, 0);
        helper.setBlock(chest, Blocks.CHEST);
        ChestBlockEntity inventory = helper.getBlockEntity(chest);
        inventory.setItem(0, new ItemStack(Items.DIAMOND, 5));
        inventory.setItem(10, new ItemStack(Items.IRON_INGOT, 64));
        QuarryControllerBlockEntity entity = quarry(helper, new BlockPos(0, 1, 0), chest, chest, QuarryRegistries.MINING_HEAD_III.get(), new ItemStack(Items.COAL));
        check(helper, entity.start(), "Start failed");
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(Blocks.AIR, chest);
            check(helper, count(entity, Items.CHEST) == 1 && count(entity, Items.DIAMOND) == 5 && count(entity, Items.IRON_INGOT) == 64,
                    "Chest or content missing or duplicated");
            check(helper, groundItems(helper).isEmpty(), "Items spilled on the ground");
        });
    }

    /** A full buffer pauses mining (OUTPUT_FULL) without dropping anything, then mining resumes. */
    @GameTest(template = "empty", batch = "mining_full", timeoutTicks = 300)
    public static void fullBufferPausesMining(GameTestHelper helper) {
        BlockPos target = new BlockPos(2, 1, 0);
        helper.setBlock(target, Blocks.STONE);
        QuarryControllerBlockEntity entity = quarry(helper, new BlockPos(0, 1, 0), target, target, QuarryRegistries.MINING_HEAD_III.get(), new ItemStack(Items.COAL));
        for (int slot = 0; slot < QuarryControllerBlockEntity.BUFFER_SLOTS; slot++) entity.buffer().setStackInSlot(slot, new ItemStack(Items.DIRT, 64));
        check(helper, entity.start(), "Start failed");
        helper.runAtTickTime(100, () -> {
            check(helper, entity.status() == QuarryStatus.OUTPUT_FULL, "Expected OUTPUT_FULL, got " + entity.status());
            helper.assertBlockPresent(Blocks.STONE, target);
            check(helper, groundItems(helper).isEmpty(), "Items thrown on the ground");
            entity.buffer().setStackInSlot(0, ItemStack.EMPTY);
        });
        helper.runAtTickTime(200, () -> {
            helper.assertBlockPresent(Blocks.AIR, target);
            check(helper, count(entity, Items.COBBLESTONE) == 1, "Mining did not resume");
            helper.succeed();
        });
    }

    /** Running out of fuel stops drilling (NO_FUEL); refuelling continues the same block. */
    @GameTest(template = "empty", batch = "mining_fuel", timeoutTicks = 400)
    public static void emptyFuelWaitsAndResumes(GameTestHelper helper) {
        BlockPos target = new BlockPos(2, 1, 0);
        helper.setBlock(target, Blocks.STONE);
        // A stick burns 100 ticks: half of what Head I needs for one block.
        QuarryControllerBlockEntity entity = quarry(helper, new BlockPos(0, 1, 0), target, target, QuarryRegistries.MINING_HEAD_I.get(), new ItemStack(Items.STICK));
        check(helper, entity.start(), "Start failed");
        helper.runAtTickTime(150, () -> {
            check(helper, entity.status() == QuarryStatus.NO_FUEL && entity.drillTicks() == 100, "Expected NO_FUEL at 100 ticks");
            helper.assertBlockPresent(Blocks.STONE, target);
            entity.fuelSlot().setStackInSlot(0, new ItemStack(Items.LAVA_BUCKET));
        });
        helper.runAtTickTime(240, () -> helper.assertBlockPresent(Blocks.STONE, target));
        helper.runAtTickTime(260, () -> {
            helper.assertBlockPresent(Blocks.AIR, target);
            check(helper, entity.fuelSlot().getStackInSlot(0).is(Items.BUCKET), "Lava bucket not returned");
            helper.succeed();
        });
    }

    /** PAUSE freezes the drill and the fuel; RESUME continues the same block. */
    @GameTest(template = "empty", batch = "mining_pause", timeoutTicks = 300)
    public static void pauseFreezesEverything(GameTestHelper helper) {
        BlockPos target = new BlockPos(2, 1, 0);
        helper.setBlock(target, Blocks.STONE);
        QuarryControllerBlockEntity entity = quarry(helper, new BlockPos(0, 1, 0), target, target, QuarryRegistries.MINING_HEAD_II.get(), new ItemStack(Items.COAL));
        check(helper, entity.start(), "Start failed");
        int[] frozen = new int[2];
        helper.runAtTickTime(50, () -> {
            check(helper, entity.pause(), "Pause refused");
            frozen[0] = entity.drillTicks();
            frozen[1] = entity.fuelTicks();
        });
        helper.runAtTickTime(150, () -> {
            check(helper, entity.drillTicks() == frozen[0] && entity.fuelTicks() == frozen[1], "Paused quarry kept working");
            check(helper, entity.status() == QuarryStatus.PAUSED, "Not PAUSED");
            helper.assertBlockPresent(Blocks.STONE, target);
            check(helper, entity.resume(), "Resume refused");
        });
        helper.runAtTickTime(150 + 120 - frozen[0] + 5, () -> {
            helper.assertBlockPresent(Blocks.AIR, target);
            helper.succeed();
        });
    }

    /** Server restart: the saved block entity resumes the same target, drill time, fuel and buffer. */
    @GameTest(template = "empty", batch = "mining_restart", timeoutTicks = 100)
    public static void restartKeepsTheJob(GameTestHelper helper) {
        BlockPos a = new BlockPos(2, 1, 0);
        BlockPos b = new BlockPos(3, 1, 0);
        helper.setBlock(a, Blocks.STONE);
        helper.setBlock(b, Blocks.STONE);
        QuarryControllerBlockEntity entity = quarry(helper, new BlockPos(0, 1, 0), a, b, QuarryRegistries.MINING_HEAD_III.get(), new ItemStack(Items.COAL, 3));
        entity.setStopY(helper.absolutePos(a).getY());
        entity.buffer().setStackInSlot(4, new ItemStack(Items.GOLD_INGOT, 7));
        check(helper, entity.start(), "Start failed");
        helper.runAtTickTime(80, () -> {
            var registries = helper.getLevel().registryAccess();
            CompoundTag saved = entity.saveWithoutMetadata(registries);
            QuarryControllerBlockEntity copy = new QuarryControllerBlockEntity(entity.getBlockPos(), entity.getBlockState());
            copy.setLevel(helper.getLevel());
            copy.loadWithComponents(saved, registries);
            check(helper, copy.cursor() == entity.cursor() && copy.drillTicks() == entity.drillTicks(), "Progress lost");
            check(helper, copy.blocksMined() == 1 && copy.fuelTicks() == entity.fuelTicks(), "Counters or fuel lost");
            check(helper, copy.running() && copy.buffer().getStackInSlot(4).getCount() == 7, "State or buffer lost");
            check(helper, copy.currentTarget().equals(helper.absolutePos(b)), "Current target lost");
            helper.succeed();
        });
    }

    static QuarryControllerBlockEntity quarry(GameTestHelper helper, BlockPos controller, BlockPos a, BlockPos b, Item head, ItemStack fuel) {
        helper.setBlock(controller, QuarryRegistries.QUARRY_I.get());
        QuarryControllerBlockEntity entity = helper.getBlockEntity(controller);
        entity.setCorners(helper.absolutePos(a), helper.absolutePos(b));
        entity.setStopY(Math.min(helper.absolutePos(a).getY(), helper.absolutePos(b).getY()));
        entity.headSlot().setStackInSlot(0, new ItemStack(head));
        entity.fuelSlot().setStackInSlot(0, fuel);
        return entity;
    }

    static int count(QuarryControllerBlockEntity entity, Item item) {
        int total = 0;
        for (int slot = 0; slot < entity.buffer().getSlots(); slot++) {
            ItemStack stack = entity.buffer().getStackInSlot(slot);
            if (stack.is(item)) total += stack.getCount();
        }
        return total;
    }

    static List<ItemEntity> groundItems(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(8));
    }

    @SuppressWarnings("unused")
    private static Block unused() {
        return Blocks.AIR;
    }
}
