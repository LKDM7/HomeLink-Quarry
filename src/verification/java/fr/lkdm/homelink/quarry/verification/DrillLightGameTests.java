package fr.lkdm.homelink.quarry.verification;

import static fr.lkdm.homelink.quarry.verification.FoundationGameTests.check;
import static fr.lkdm.homelink.quarry.verification.MiningGameTests.quarry;

import fr.lkdm.homelink.quarry.block.DrillLightBlock;
import fr.lkdm.homelink.quarry.blockentity.QuarryControllerBlockEntity;
import fr.lkdm.homelink.quarry.registry.QuarryRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The working Mining Head lights its surroundings like a torch, and never leaves light behind. */
@GameTestHolder(QuarryValidation.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DrillLightGameTests {
    @GameTest(template = "empty", batch = "light_a", timeoutTicks = 120)
    public static void lightFollowsTheWorkingHead(GameTestHelper helper) {
        BlockPos a = new BlockPos(2, 1, 0);
        BlockPos b = new BlockPos(3, 1, 0);
        helper.setBlock(a, Blocks.STONE);
        helper.setBlock(b, Blocks.STONE);
        QuarryControllerBlockEntity entity = quarry(helper, new BlockPos(0, 1, 0), a, b, QuarryRegistries.MINING_HEAD_I.get(),
                new ItemStack(Items.COAL));
        check(helper, entity.start(), "Start");
        helper.runAtTickTime(20, () -> {
            BlockPos lit = helper.absolutePos(a.above());
            check(helper, helper.getLevel().getBlockState(lit).is(QuarryRegistries.DRILL_LIGHT.get()), "No light over the drilled block");
            check(helper, helper.getLevel().getBrightness(LightLayer.BLOCK, lit) == DrillLightBlock.LIGHT, "Light level is not a torch's (14)");
            check(helper, helper.getLevel().getBrightness(LightLayer.BLOCK, lit.east(3)) == DrillLightBlock.LIGHT - 3, "Light does not spread");
            check(helper, entity.pause(), "Pause");
        });
        helper.runAtTickTime(23, () -> {
            helper.assertBlockPresent(Blocks.AIR, a.above());
            check(helper, entity.drillLight() == null, "Light kept while paused");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "light_b", timeoutTicks = 120)
    public static void abandonedLightExpires(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, QuarryRegistries.DRILL_LIGHT.get());
        helper.runAtTickTime(40, () -> check(helper, helper.getBlockState(pos).is(QuarryRegistries.DRILL_LIGHT.get()), "Expired too early"));
        helper.runAtTickTime(100, () -> {
            helper.assertBlockPresent(Blocks.AIR, pos);
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "light_c", timeoutTicks = 80)
    public static void lightNeverReplacesABlock(GameTestHelper helper) {
        BlockPos target = new BlockPos(2, 1, 0);
        helper.setBlock(target, Blocks.STONE);
        helper.setBlock(target.above(), Blocks.GLASS);
        QuarryControllerBlockEntity entity = quarry(helper, new BlockPos(0, 1, 0), target, target, QuarryRegistries.MINING_HEAD_I.get(),
                new ItemStack(Items.COAL));
        check(helper, entity.start(), "Start");
        helper.runAtTickTime(20, () -> {
            helper.assertBlockPresent(Blocks.GLASS, target.above());
            check(helper, entity.drillLight() == null, "Light placed inside a block");
            helper.succeed();
        });
    }
}
