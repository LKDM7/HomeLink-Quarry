package fr.lkdm.homelink.quarry.verification;

import static fr.lkdm.homelink.quarry.verification.FoundationGameTests.check;
import static fr.lkdm.homelink.quarry.verification.MiningHeadGameTests.place;

import fr.lkdm.homelink.quarry.blockentity.QuarryControllerBlockEntity;
import fr.lkdm.homelink.quarry.quarry.MiningHeadTier;
import fr.lkdm.homelink.quarry.quarry.QuarryFuel;
import fr.lkdm.homelink.quarry.registry.QuarryRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Phase 4: vanilla burn values, runtime only, never speed. */
@GameTestHolder(QuarryValidation.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FuelGameTests {
    @GameTest(template = "empty")
    public static void vanillaBurnTimes(GameTestHelper helper) {
        check(helper, QuarryFuel.burnTicks(new ItemStack(Items.COAL)) == 1600, "Coal");
        check(helper, QuarryFuel.burnTicks(new ItemStack(Items.CHARCOAL)) == 1600, "Charcoal");
        check(helper, QuarryFuel.burnTicks(new ItemStack(Items.COAL_BLOCK)) == 16000, "Coal Block");
        check(helper, QuarryFuel.burnTicks(new ItemStack(Items.LAVA_BUCKET)) == 20000, "Lava Bucket");
        check(helper, QuarryFuel.isFuel(new ItemStack(Items.OAK_PLANKS)), "Other vanilla fuels are accepted");
        check(helper, !QuarryFuel.isFuel(new ItemStack(Items.DIRT)) && !QuarryFuel.isFuel(new ItemStack(Items.BUCKET)), "Non-fuel accepted");
        helper.succeed();
    }

    /** Fuel sets runtime; the head sets blocks per second. Same fuel, faster head = more blocks, same speed. */
    @GameTest(template = "empty")
    public static void fuelIsRuntimeOnly(GameTestHelper helper) {
        QuarryControllerBlockEntity entity = place(helper, QuarryRegistries.QUARRY_I.get(), new BlockPos(1, 2, 1));
        entity.fuelSlot().setStackInSlot(0, new ItemStack(Items.COAL, 4));
        check(helper, entity.runtimeTicks() == 6400, "4 coal = 6400 running ticks");
        check(helper, entity.estimatedBlocks() == 0, "No estimate without a head");
        entity.headSlot().setStackInSlot(0, new ItemStack(QuarryRegistries.MINING_HEAD_I.get()));
        check(helper, entity.estimatedBlocks() == 32, "Head I: 6400 / 200 = 32 blocks");
        entity.headSlot().setStackInSlot(0, new ItemStack(QuarryRegistries.MINING_HEAD_III.get()));
        check(helper, entity.estimatedBlocks() == 106, "Head III: 6400 / 60 = 106 blocks");
        check(helper, entity.miningHead().orElseThrow().ticksPerBlock() == MiningHeadTier.III.ticksPerBlock(), "Fuel changed the speed");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void lavaLeavesTheBucket(GameTestHelper helper) {
        QuarryControllerBlockEntity entity = place(helper, QuarryRegistries.QUARRY_II.get(), new BlockPos(1, 2, 1));
        entity.fuelSlot().setStackInSlot(0, new ItemStack(Items.LAVA_BUCKET));
        check(helper, entity.refuel() && entity.fuelTicks() == 20000, "Lava not burnt");
        check(helper, entity.fuelSlot().getStackInSlot(0).is(Items.BUCKET), "Empty bucket lost");
        check(helper, entity.fuelPercent() == 63, "Fuel percentage of a 32000 tank");
        check(helper, !entity.automation().extractItem(0, 1, true).isEmpty(), "Hoppers must retrieve the empty bucket");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void tankNeverOverflows(GameTestHelper helper) {
        QuarryControllerBlockEntity entity = place(helper, QuarryRegistries.QUARRY_I.get(), new BlockPos(1, 2, 1));
        entity.fuelSlot().setStackInSlot(0, new ItemStack(Items.COAL_BLOCK, 3));
        check(helper, entity.refuel() && entity.refuel(), "Two coal blocks fit");
        check(helper, !entity.refuel() && entity.fuelTicks() == QuarryFuel.CAPACITY, "Third coal block overflowed the tank");
        check(helper, entity.fuelSlot().getStackInSlot(0).getCount() == 1, "Fuel item lost");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void hopperFeedsFuelOnly(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        QuarryControllerBlockEntity entity = place(helper, QuarryRegistries.QUARRY_I.get(), pos);
        var handler = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(pos), Direction.UP);
        check(helper, handler != null, "No item handler exposed");
        check(helper, handler.insertItem(0, new ItemStack(Items.COAL, 10), false).isEmpty(), "Coal refused");
        check(helper, !handler.insertItem(0, new ItemStack(Items.DIRT), false).isEmpty(), "Dirt accepted");
        check(helper, !handler.insertItem(1, new ItemStack(Items.COAL), false).isEmpty(), "Buffer accepted an insertion");
        check(helper, handler.extractItem(0, 1, false).isEmpty(), "Fuel extracted by automation");
        check(helper, entity.fuelSlot().getStackInSlot(0).getCount() == 10, "Fuel slot content");
        var registries = helper.getLevel().registryAccess();
        entity.refuel();
        CompoundTag saved = entity.saveWithoutMetadata(registries);
        QuarryControllerBlockEntity copy = place(helper, QuarryRegistries.QUARRY_I.get(), new BlockPos(3, 2, 1));
        copy.loadWithComponents(saved, registries);
        check(helper, copy.fuelTicks() == 1600 && copy.fuelSlot().getStackInSlot(0).getCount() == 9, "Fuel lost on reload");
        helper.succeed();
    }
}
