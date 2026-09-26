package fr.lkdm.homelink.quarry.verification;

import static fr.lkdm.homelink.quarry.verification.FoundationGameTests.check;
import static fr.lkdm.homelink.quarry.verification.MiningHeadGameTests.place;

import fr.lkdm.homelink.quarry.blockentity.QuarryControllerBlockEntity;
import fr.lkdm.homelink.quarry.quarry.AreaCheck;
import fr.lkdm.homelink.quarry.quarry.QuarryArea;
import fr.lkdm.homelink.quarry.quarry.QuarryStatus;
import fr.lkdm.homelink.quarry.registry.QuarryRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Phase 3: area selection, depth, validation and deterministic progression. */
@GameTestHolder(QuarryValidation.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AreaGameTests {
    @GameTest(template = "empty")
    public static void areaMustFitTheLevel(GameTestHelper helper) {
        QuarryControllerBlockEntity one = controller(helper, QuarryRegistries.QUARRY_I.get(), 0);
        BlockPos o = one.getBlockPos().offset(0, 0, 3);
        one.setCorners(o.offset(0, 60 - 62, 0), o.offset(7, 60 - 62, 7));
        check(helper, one.checkArea() == AreaCheck.VALID, "Quarry I 8x8 must be valid");
        one.setCorners(o.offset(0, 60 - 62, 0), o.offset(3, 60 - 62, 3));
        check(helper, one.checkArea() == AreaCheck.VALID, "Smaller area must be valid");
        one.setCorners(o.offset(0, 60 - 62, 0), o.offset(8, 60 - 62, 7));
        check(helper, one.checkArea() == AreaCheck.TOO_LARGE && one.status() == QuarryStatus.INVALID_AREA, "Quarry I 9x8 must be invalid");

        QuarryControllerBlockEntity two = controller(helper, QuarryRegistries.QUARRY_II.get(), 2);
        two.setCorners(o.offset(13, 60 - 62, 11), o.offset(0, 64 - 62, 0));
        QuarryArea area = two.area().orElseThrow();
        check(helper, area.width() == 14 && area.length() == 12 && area.startY() == o.getY() + 2, "Corner order must not matter");
        check(helper, two.checkArea() == AreaCheck.VALID, "Quarry II 14x12 must be valid");
        two.setCorners(o.offset(0, 60 - 62, 0), o.offset(19, 60 - 62, 11));
        check(helper, two.checkArea() == AreaCheck.TOO_LARGE, "Quarry II 20x12 must be invalid");

        QuarryControllerBlockEntity three = controller(helper, QuarryRegistries.QUARRY_III.get(), 4);
        three.setCorners(o.offset(0, 60 - 62, 0), o.offset(31, 60 - 62, 31));
        check(helper, three.checkArea() == AreaCheck.VALID, "Quarry III 32x32 must be valid");
        three.setCorners(o.offset(0, 60 - 62, 0), o.offset(32, 60 - 62, 31));
        check(helper, three.checkArea() == AreaCheck.TOO_LARGE, "Quarry III 33x32 must be invalid");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void stopYIsClampedToTheDimension(GameTestHelper helper) {
        QuarryControllerBlockEntity entity = controller(helper, QuarryRegistries.QUARRY_I.get(), 0);
        int min = helper.getLevel().getMinBuildHeight();
        entity.setCorners(new BlockPos(0, 72, 0), new BlockPos(3, 70, 3));
        check(helper, entity.area().orElseThrow().stopY() == min, "Default Stop Y must be the dimension bottom");
        entity.setStopY(-32);
        QuarryArea area = entity.area().orElseThrow();
        check(helper, area.startY() == 72 && area.stopY() == -32 && area.layers() == 105, "Start 72 / Stop -32");
        entity.setStopY(min - 50);
        check(helper, entity.area().orElseThrow().stopY() == min, "Stop Y below the world must clamp");
        entity.setStopY(200);
        check(helper, entity.area().orElseThrow().stopY() == 72, "Stop Y above Start Y must clamp");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void orderIsXThenZThenLayer(GameTestHelper helper) {
        QuarryArea area = new QuarryArea(10, 20, 13, 22, 64, 62);
        check(helper, area.totalPositions() == 4 * 3 * 3, "Total positions");
        check(helper, area.target(0).equals(new BlockPos(10, 64, 20)), "First target");
        check(helper, area.target(1).equals(new BlockPos(11, 64, 20)), "X advances first");
        check(helper, area.target(4).equals(new BlockPos(10, 64, 21)), "Then Z");
        check(helper, area.target(12).equals(new BlockPos(10, 63, 20)), "Then the next layer down");
        check(helper, area.target(35).equals(new BlockPos(13, 62, 22)), "Last target");
        for (long i = 0; i < area.totalPositions(); i++) check(helper, area.contains(area.target(i)), "Target outside area");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void farAreaIsRejected(GameTestHelper helper) {
        QuarryControllerBlockEntity entity = controller(helper, QuarryRegistries.QUARRY_I.get(), 0);
        BlockPos far = entity.getBlockPos().offset(500, 0, 0);
        entity.setCorners(far, far.offset(3, 0, 3));
        check(helper, entity.checkArea() == AreaCheck.TOO_FAR, "Area 500 blocks away must be rejected");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void controlLocksAndKeepsTheJob(GameTestHelper helper) {
        QuarryControllerBlockEntity entity = controller(helper, QuarryRegistries.QUARRY_I.get(), 0);
        BlockPos origin = entity.getBlockPos();
        entity.setCorners(origin.offset(2, 0, 2), origin.offset(5, 0, 5));
        check(helper, !entity.start() && entity.status() == QuarryStatus.NO_HEAD, "Started without a head");
        entity.headSlot().setStackInSlot(0, new ItemStack(QuarryRegistries.MINING_HEAD_I.get()));
        check(helper, !entity.start() && entity.status() == QuarryStatus.NO_FUEL, "Started without fuel");
        entity.fuelSlot().setStackInSlot(0, new ItemStack(net.minecraft.world.item.Items.COAL));
        check(helper, entity.start() && entity.running(), "Valid quarry did not start");
        check(helper, !entity.setCorners(origin, origin.offset(1, 0, 1)) && !entity.setStopY(0), "Configuration not locked while running");
        check(helper, entity.pause() && entity.status() == QuarryStatus.PAUSED, "Pause");
        check(helper, entity.resume() && !entity.paused(), "Resume");
        check(helper, entity.stop() && !entity.running() && entity.area().isPresent(), "Stop must keep the area");
        check(helper, entity.start(), "Restart after stop");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void configurationSurvivesReload(GameTestHelper helper) {
        QuarryControllerBlockEntity entity = controller(helper, QuarryRegistries.QUARRY_II.get(), 0);
        BlockPos origin = entity.getBlockPos();
        entity.setCorners(origin.offset(2, 3, 2), origin.offset(11, 0, 15));
        entity.setStopY(origin.getY() - 10);
        entity.headSlot().setStackInSlot(0, new ItemStack(QuarryRegistries.MINING_HEAD_II.get()));
        entity.fuelSlot().setStackInSlot(0, new ItemStack(net.minecraft.world.item.Items.COAL));
        check(helper, entity.start(), "Start");
        entity.pause();
        var registries = helper.getLevel().registryAccess();
        CompoundTag saved = entity.saveWithoutMetadata(registries);
        QuarryControllerBlockEntity copy = controller(helper, QuarryRegistries.QUARRY_II.get(), 4);
        copy.loadWithComponents(saved, registries);
        check(helper, copy.area().equals(entity.area()) && copy.running() && copy.paused(), "Area or state lost on reload");
        check(helper, copy.currentTarget().equals(entity.currentTarget()), "Current target lost on reload");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void markerAppliesCorners(GameTestHelper helper) {
        QuarryControllerBlockEntity entity = controller(helper, QuarryRegistries.QUARRY_I.get(), 0);
        BlockPos origin = entity.getBlockPos();
        ItemStack marker = new ItemStack(QuarryRegistries.QUARRY_MARKER.get());
        var dimension = helper.getLevel().dimension();
        marker.set(QuarryRegistries.CORNER_A.get(), GlobalPos.of(dimension, origin.offset(1, 0, 1)));
        marker.set(QuarryRegistries.CORNER_B.get(), GlobalPos.of(dimension, origin.offset(6, 0, 4)));
        var player = FakePlayerFactory.getMinecraft(helper.getLevel());
        player.setItemInHand(InteractionHand.MAIN_HAND, marker);
        helper.getLevel().getBlockState(origin).useItemOn(marker, helper.getLevel(), player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(origin), Direction.UP, origin, false));
        QuarryArea area = entity.area().orElseThrow();
        check(helper, area.width() == 6 && area.length() == 4, "Marker corners not applied");
        helper.succeed();
    }

    private static QuarryControllerBlockEntity controller(GameTestHelper helper, net.minecraft.world.level.block.Block block, int x) {
        return place(helper, block, new BlockPos(x, 2, 0));
    }
}
