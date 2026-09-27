package fr.lkdm.homelink.quarry.verification;

import static fr.lkdm.homelink.quarry.verification.FoundationGameTests.check;
import static fr.lkdm.homelink.quarry.verification.MiningHeadGameTests.place;

import fr.lkdm.homecore.api.energy.EnergyApi;
import fr.lkdm.homecore.api.energy.EnergyPort;
import fr.lkdm.homecore.api.energy.EnergyRole;
import fr.lkdm.homelink.quarry.blockentity.QuarryControllerBlockEntity;
import fr.lkdm.homelink.quarry.quarry.MiningHeadTier;
import fr.lkdm.homelink.quarry.quarry.QuarryEnergy;
import fr.lkdm.homelink.quarry.registry.QuarryRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The quarry runs on HomeLink Energy only: HE sets runtime, never speed, and fuel is no longer accepted. */
@GameTestHolder(QuarryValidation.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EnergyGameTests {
    @GameTest(template = "empty")
    public static void energyInputOnEveryFace(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        place(helper, QuarryRegistries.QUARRY_I.get(), pos);
        for (Direction side : Direction.values()) {
            EnergyPort port = helper.getLevel().getCapability(EnergyApi.BLOCK, helper.absolutePos(pos), side);
            check(helper, port != null, "No energy port on " + side);
            check(helper, port.role() == EnergyRole.CONSUMER && port.type().canReceive() && !port.type().canSend(), "Not an input-only consumer");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void perBlockCostIsExact(GameTestHelper helper) {
        for (MiningHeadTier tier : MiningHeadTier.values()) {
            long total = 0;
            for (int tick = 1; tick <= tier.ticksPerBlock(); tick++) total += QuarryEnergy.forDrillTick(tick, tier.ticksPerBlock(), 100);
            check(helper, total == 100, "Head " + tier + " spends " + total + " HE for a 100 HE block");
        }
        check(helper, QuarryEnergy.capacity() == QuarryEnergy.perBlock() * QuarryEnergy.BUFFER_BLOCKS, "Buffer size");
        helper.succeed();
    }

    /** Energy sets runtime; the head sets blocks per second. Same HE, faster head = same blocks, sooner. */
    @GameTest(template = "empty")
    public static void energyIsRuntimeOnly(GameTestHelper helper) {
        QuarryControllerBlockEntity entity = place(helper, QuarryRegistries.QUARRY_I.get(), new BlockPos(1, 2, 1));
        check(helper, entity.energyPort().insert(2_000, false) == 2_000, "HE refused");
        check(helper, entity.estimatedBlocks() == 0 && entity.runtimeTicks() == 0, "No estimate without a head");
        entity.headSlot().setStackInSlot(0, new ItemStack(QuarryRegistries.MINING_HEAD_I.get()));
        check(helper, entity.estimatedBlocks() == 20 && entity.runtimeTicks() == 4_000, "Head I: 20 blocks of 200 ticks");
        entity.headSlot().setStackInSlot(0, new ItemStack(QuarryRegistries.MINING_HEAD_III.get()));
        check(helper, entity.estimatedBlocks() == 20 && entity.runtimeTicks() == 1_200, "Head III: 20 blocks of 60 ticks");
        check(helper, entity.energyPercent() == 40, "2000 of a 5000 HE buffer");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void bufferNeverOverflows(GameTestHelper helper) {
        QuarryControllerBlockEntity entity = place(helper, QuarryRegistries.QUARRY_I.get(), new BlockPos(1, 2, 1));
        long capacity = QuarryEnergy.capacity();
        check(helper, entity.energyPort().insert(Long.MAX_VALUE, false) == capacity, "Buffer did not fill exactly");
        check(helper, entity.energyPort().insert(1, false) == 0 && entity.storedEnergy() == capacity, "Buffer overflowed");
        check(helper, entity.energyPort().extract(10, false) == 0, "HE extracted from a consumer");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void hoppersCannotFeedFuel(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        QuarryControllerBlockEntity entity = place(helper, QuarryRegistries.QUARRY_I.get(), pos);
        var handler = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(pos), Direction.UP);
        check(helper, handler != null && handler.getSlots() == QuarryControllerBlockEntity.BUFFER_SLOTS, "Automation must expose the buffer only");
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            check(helper, handler.insertItem(slot, new ItemStack(Items.COAL, 10), false).getCount() == 10, "Coal accepted in slot " + slot);
        }
        entity.buffer().setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 3));
        check(helper, handler.extractItem(0, 3, false).getCount() == 3, "Buffer not extractable");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void energySurvivesReload(GameTestHelper helper) {
        QuarryControllerBlockEntity entity = place(helper, QuarryRegistries.QUARRY_II.get(), new BlockPos(1, 2, 1));
        entity.energyPort().insert(1_234, false);
        var registries = helper.getLevel().registryAccess();
        CompoundTag saved = entity.saveWithoutMetadata(registries);
        QuarryControllerBlockEntity copy = place(helper, QuarryRegistries.QUARRY_II.get(), new BlockPos(3, 2, 1));
        copy.loadWithComponents(saved, registries);
        check(helper, copy.storedEnergy() == 1_234, "HE lost on reload: " + copy.storedEnergy());
        helper.succeed();
    }

    /** A quarry saved with the old fuel slot gives its fuel back on the ground instead of deleting it. */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void oldFuelIsDroppedNotDeleted(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        QuarryControllerBlockEntity entity = place(helper, QuarryRegistries.QUARRY_I.get(), pos);
        var registries = helper.getLevel().registryAccess();
        CompoundTag saved = entity.saveWithoutMetadata(registries);
        var oldSlot = new net.neoforged.neoforge.items.ItemStackHandler(1);
        oldSlot.setStackInSlot(0, new ItemStack(Items.COAL, 7));
        saved.put("fuel", oldSlot.serializeNBT(registries));
        saved.putInt("fuel_ticks", 1600);
        entity.loadWithComponents(saved, registries);
        helper.succeedWhen(() -> {
            var drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(pos)).inflate(3));
            check(helper, drops.stream().anyMatch(drop -> drop.getItem().is(Items.COAL) && drop.getItem().getCount() == 7), "Old fuel not dropped");
            check(helper, entity.storedEnergy() == 0, "Old fuel was converted into HE");
        });
    }
}
