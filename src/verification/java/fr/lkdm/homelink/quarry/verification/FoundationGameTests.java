package fr.lkdm.homelink.quarry.verification;

import fr.lkdm.homecore.registry.HomeCoreItems;
import fr.lkdm.homelink.quarry.HomeLinkQuarry;
import fr.lkdm.homelink.quarry.block.QuarryControllerBlock;
import fr.lkdm.homelink.quarry.blockentity.QuarryControllerBlockEntity;
import fr.lkdm.homelink.quarry.quarry.QuarryTier;
import fr.lkdm.homelink.quarry.registry.QuarryRegistries;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Phase 1: registries, level limits, placement, recipes and loot. */
@GameTestHolder(QuarryValidation.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FoundationGameTests {
    @GameTest(template = "empty")
    public static void levelLimitsBoundAreaOnly(GameTestHelper helper) {
        check(helper, QuarryTier.I.allows(8, 8) && QuarryTier.I.allows(4, 4) && QuarryTier.I.allows(6, 8), "Quarry I rejects a valid area");
        check(helper, !QuarryTier.I.allows(9, 8) && !QuarryTier.I.allows(12, 12), "Quarry I accepts an oversized area");
        check(helper, QuarryTier.II.allows(8, 8) && QuarryTier.II.allows(10, 14) && QuarryTier.II.allows(16, 16), "Quarry II rejects a valid area");
        check(helper, !QuarryTier.II.allows(17, 16) && !QuarryTier.II.allows(20, 20), "Quarry II accepts an oversized area");
        check(helper, QuarryTier.III.allows(16, 16) && QuarryTier.III.allows(20, 24) && QuarryTier.III.allows(32, 32), "Quarry III rejects a valid area");
        check(helper, !QuarryTier.III.allows(33, 32) && !QuarryTier.III.allows(64, 64), "Quarry III accepts an oversized area");
        check(helper, !QuarryTier.III.allows(0, 4) && !QuarryTier.III.allows(4, -1), "Empty area accepted");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void controllersPlaceWithLevelAndFacing(GameTestHelper helper) {
        List<Block> blocks = List.of(QuarryRegistries.QUARRY_I.get(), QuarryRegistries.QUARRY_II.get(), QuarryRegistries.QUARRY_III.get());
        for (int i = 0; i < blocks.size(); i++) {
            BlockPos pos = new BlockPos(i, 1, 0);
            helper.setBlock(pos, blocks.get(i).defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.EAST));
            QuarryControllerBlockEntity entity = helper.getBlockEntity(pos);
            check(helper, entity != null && entity.tier() == QuarryTier.values()[i], "Wrong level for " + blocks.get(i));
            check(helper, ((QuarryControllerBlock) blocks.get(i)).tier().level() == i + 1, "Level number mismatch");
            check(helper, helper.getBlockState(pos).getValue(HorizontalDirectionalBlock.FACING) == Direction.EAST, "Facing lost");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void recipesAreProgressive(GameTestHelper helper) {
        Recipe<?> first = recipe(helper, "quarry_i");
        Recipe<?> second = recipe(helper, "quarry_ii");
        Recipe<?> third = recipe(helper, "quarry_iii");
        check(helper, uses(first, HomeCoreItems.HOMELINK_CIRCUIT_BOARD.get()), "Quarry I must use the HomeLink Circuit Board");
        check(helper, uses(second, QuarryRegistries.QUARRY_I_ITEM.get()) && uses(second, HomeCoreItems.HOMELINK_MICROPROCESSOR.get()),
                "Quarry II must upgrade Quarry I with a Microprocessor");
        check(helper, uses(third, QuarryRegistries.QUARRY_II_ITEM.get()) && count(third, HomeCoreItems.HOMELINK_MICROPROCESSOR.get()) >= 2,
                "Quarry III must upgrade Quarry II with several Microprocessors");
        ItemStack result = third.getResultItem(helper.getLevel().registryAccess());
        check(helper, result.is(QuarryRegistries.QUARRY_III_ITEM.get()), "Quarry III recipe result");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void controllersDropThemselves(GameTestHelper helper) {
        for (String name : List.of("quarry_i", "quarry_ii", "quarry_iii")) {
            ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE, HomeLinkQuarry.id("blocks/" + name));
            LootTable table = helper.getLevel().getServer().reloadableRegistries().getLootTable(key);
            check(helper, table != LootTable.EMPTY, "Missing loot table " + name);
        }
        helper.succeed();
    }

    private static Recipe<?> recipe(GameTestHelper helper, String name) {
        var holder = helper.getLevel().getRecipeManager().byKey(HomeLinkQuarry.id(name));
        check(helper, holder.isPresent(), "Missing recipe " + name);
        return holder.get().value();
    }

    private static boolean uses(Recipe<?> recipe, Item item) {
        return count(recipe, item) > 0;
    }

    private static long count(Recipe<?> recipe, Item item) {
        return recipe.getIngredients().stream().filter((Ingredient ingredient) -> ingredient.test(new ItemStack(item))).count();
    }

    static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }
}
