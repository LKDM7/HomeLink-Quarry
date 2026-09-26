package fr.lkdm.homelink.quarry.verification;

import static fr.lkdm.homelink.quarry.verification.FoundationGameTests.check;

import fr.lkdm.homecore.registry.HomeCoreItems;
import fr.lkdm.homelink.quarry.HomeLinkQuarry;
import fr.lkdm.homelink.quarry.blockentity.QuarryControllerBlockEntity;
import fr.lkdm.homelink.quarry.item.MiningHeadItem;
import fr.lkdm.homelink.quarry.quarry.MiningHeadTier;
import fr.lkdm.homelink.quarry.quarry.QuarryTier;
import fr.lkdm.homelink.quarry.registry.QuarryRegistries;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Phase 2: Mining Heads, their dedicated slot and head/quarry independence. */
@GameTestHolder(QuarryValidation.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MiningHeadGameTests {
    @GameTest(template = "empty")
    public static void headSpeedsAreExact(GameTestHelper helper) {
        check(helper, MiningHeadTier.I.ticksPerBlock() == 200 && MiningHeadTier.I.secondsPerBlock() == 10, "Head I must drill 1 block / 10 s");
        check(helper, MiningHeadTier.II.ticksPerBlock() == 120 && MiningHeadTier.II.secondsPerBlock() == 6, "Head II must drill 1 block / 6 s");
        check(helper, MiningHeadTier.III.ticksPerBlock() == 60 && MiningHeadTier.III.secondsPerBlock() == 3, "Head III must drill 1 block / 3 s");
        for (MiningHeadItem item : heads()) {
            ItemStack stack = new ItemStack(item);
            check(helper, !stack.isDamageableItem() && stack.getMaxStackSize() == 1, "Heads are permanent and unstackable");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void slotAcceptsOnlyHeads(GameTestHelper helper) {
        QuarryControllerBlockEntity entity = place(helper, QuarryRegistries.QUARRY_I.get(), new BlockPos(1, 1, 1));
        var slot = entity.headSlot();
        check(helper, !slot.insertItem(0, new ItemStack(Items.DIAMOND_PICKAXE), true).isEmpty(), "Slot accepted a pickaxe");
        check(helper, !slot.insertItem(0, new ItemStack(Items.COAL), true).isEmpty(), "Slot accepted fuel");
        check(helper, entity.miningHead().isEmpty(), "Empty slot reports a head");
        check(helper, slot.insertItem(0, new ItemStack(QuarryRegistries.MINING_HEAD_II.get()), false).isEmpty(), "Slot refused Head II");
        check(helper, !slot.insertItem(0, new ItemStack(QuarryRegistries.MINING_HEAD_I.get()), true).isEmpty(), "Slot holds more than one head");
        check(helper, entity.miningHead().orElse(null) == MiningHeadTier.II, "Installed head not reported");
        helper.succeed();
    }

    /** Every quarry level works with every head: level = area, head = speed. */
    @GameTest(template = "empty")
    public static void allNineCombinations(GameTestHelper helper) {
        List<Block> quarries = List.of(QuarryRegistries.QUARRY_I.get(), QuarryRegistries.QUARRY_II.get(), QuarryRegistries.QUARRY_III.get());
        List<MiningHeadItem> heads = heads();
        int x = 0;
        for (int q = 0; q < 3; q++) {
            for (int h = 0; h < 3; h++) {
                QuarryControllerBlockEntity entity = place(helper, quarries.get(q), new BlockPos(x++, 1, 3));
                entity.headSlot().setStackInSlot(0, new ItemStack(heads.get(h)));
                check(helper, entity.tier() == QuarryTier.values()[q], "Level changed with the head");
                check(helper, entity.miningHead().orElseThrow() == MiningHeadTier.values()[h], "Head changed with the level");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void headSurvivesSaveAndLoad(GameTestHelper helper) {
        QuarryControllerBlockEntity entity = place(helper, QuarryRegistries.QUARRY_III.get(), new BlockPos(2, 1, 2));
        entity.headSlot().setStackInSlot(0, new ItemStack(QuarryRegistries.MINING_HEAD_III.get()));
        var registries = helper.getLevel().registryAccess();
        CompoundTag saved = entity.saveWithoutMetadata(registries);
        QuarryControllerBlockEntity reloaded = place(helper, QuarryRegistries.QUARRY_III.get(), new BlockPos(4, 1, 2));
        reloaded.loadWithComponents(saved, registries);
        check(helper, reloaded.miningHead().orElse(null) == MiningHeadTier.III, "Head lost after reload");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void headRecipesAreProgressive(GameTestHelper helper) {
        check(helper, uses(helper, "mining_head_i", new ItemStack(HomeCoreItems.HOMELINK_CIRCUIT_BOARD.get())), "Head I needs a Circuit Board");
        check(helper, uses(helper, "mining_head_ii", new ItemStack(QuarryRegistries.MINING_HEAD_I.get()))
                && uses(helper, "mining_head_ii", new ItemStack(HomeCoreItems.HOMELINK_MICROPROCESSOR.get()))
                && uses(helper, "mining_head_ii", new ItemStack(Items.DIAMOND)), "Head II upgrades Head I");
        check(helper, uses(helper, "mining_head_iii", new ItemStack(QuarryRegistries.MINING_HEAD_II.get()))
                && uses(helper, "mining_head_iii", new ItemStack(Items.NETHERITE_INGOT))
                && uses(helper, "mining_head_iii", new ItemStack(HomeCoreItems.HOMELINK_MICROPROCESSOR.get())), "Head III upgrades Head II");
        helper.succeed();
    }

    static List<MiningHeadItem> heads() {
        return List.of(QuarryRegistries.MINING_HEAD_I.get(), QuarryRegistries.MINING_HEAD_II.get(), QuarryRegistries.MINING_HEAD_III.get());
    }

    static QuarryControllerBlockEntity place(GameTestHelper helper, Block block, BlockPos pos) {
        helper.setBlock(pos, block);
        return helper.getBlockEntity(pos);
    }

    private static boolean uses(GameTestHelper helper, String recipe, ItemStack stack) {
        Recipe<?> value = helper.getLevel().getRecipeManager().byKey(HomeLinkQuarry.id(recipe)).orElseThrow().value();
        return value.getIngredients().stream().anyMatch(ingredient -> ingredient.test(stack));
    }
}
