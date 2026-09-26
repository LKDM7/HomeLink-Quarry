package fr.lkdm.homelink.quarry.registry;

import fr.lkdm.homelink.quarry.HomeLinkQuarry;
import fr.lkdm.homelink.quarry.block.QuarryControllerBlock;
import fr.lkdm.homelink.quarry.blockentity.QuarryControllerBlockEntity;
import fr.lkdm.homelink.quarry.item.MiningHeadItem;
import fr.lkdm.homelink.quarry.item.QuarryMarkerItem;
import fr.lkdm.homelink.quarry.menu.QuarryMenu;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.minecraft.core.component.DataComponentType;
import fr.lkdm.homelink.quarry.quarry.MiningHeadTier;
import fr.lkdm.homelink.quarry.quarry.QuarryTier;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Every registry entry owned by HomeLink Quarry. */
public final class QuarryRegistries {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(HomeLinkQuarry.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(HomeLinkQuarry.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, HomeLinkQuarry.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, HomeLinkQuarry.MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, HomeLinkQuarry.MOD_ID);
    public static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, HomeLinkQuarry.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<GlobalPos>> CORNER_A =
            DATA_COMPONENTS.registerComponentType("corner_a", builder -> builder.persistent(GlobalPos.CODEC)
                    .networkSynchronized(GlobalPos.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<GlobalPos>> CORNER_B =
            DATA_COMPONENTS.registerComponentType("corner_b", builder -> builder.persistent(GlobalPos.CODEC)
                    .networkSynchronized(GlobalPos.STREAM_CODEC));

    public static final DeferredBlock<QuarryControllerBlock> QUARRY_I = quarry("quarry_i", QuarryTier.I);
    public static final DeferredBlock<QuarryControllerBlock> QUARRY_II = quarry("quarry_ii", QuarryTier.II);
    public static final DeferredBlock<QuarryControllerBlock> QUARRY_III = quarry("quarry_iii", QuarryTier.III);
    /** Light carried by a working Mining Head; no item, behaves like air. */
    public static final DeferredBlock<fr.lkdm.homelink.quarry.block.DrillLightBlock> DRILL_LIGHT = BLOCKS.register("drill_light",
            () -> new fr.lkdm.homelink.quarry.block.DrillLightBlock(net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()
                    .replaceable().noCollission().noLootTable().air()
                    .lightLevel(state -> fr.lkdm.homelink.quarry.block.DrillLightBlock.LIGHT)
                    .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)));

    public static final DeferredItem<BlockItem> QUARRY_I_ITEM =
            ITEMS.registerSimpleBlockItem(QUARRY_I, new Item.Properties());
    public static final DeferredItem<BlockItem> QUARRY_II_ITEM =
            ITEMS.registerSimpleBlockItem(QUARRY_II, new Item.Properties().rarity(Rarity.UNCOMMON));
    public static final DeferredItem<BlockItem> QUARRY_III_ITEM =
            ITEMS.registerSimpleBlockItem(QUARRY_III, new Item.Properties().rarity(Rarity.RARE).fireResistant());

    public static final DeferredItem<MiningHeadItem> MINING_HEAD_I = ITEMS.register("mining_head_i",
            () -> new MiningHeadItem(MiningHeadTier.I, new Item.Properties()));
    public static final DeferredItem<MiningHeadItem> MINING_HEAD_II = ITEMS.register("mining_head_ii",
            () -> new MiningHeadItem(MiningHeadTier.II, new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final DeferredItem<MiningHeadItem> MINING_HEAD_III = ITEMS.register("mining_head_iii",
            () -> new MiningHeadItem(MiningHeadTier.III, new Item.Properties().rarity(Rarity.RARE).fireResistant()));

    public static final DeferredItem<QuarryMarkerItem> QUARRY_MARKER = ITEMS.register("quarry_marker",
            () -> new QuarryMarkerItem(new Item.Properties()));

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<QuarryControllerBlockEntity>> QUARRY_CONTROLLER =
            BLOCK_ENTITY_TYPES.register("quarry_controller", () -> BlockEntityType.Builder.of(QuarryControllerBlockEntity::new,
                    QUARRY_I.get(), QUARRY_II.get(), QUARRY_III.get()).build(null));

    public static final DeferredHolder<MenuType<?>, MenuType<QuarryMenu>> QUARRY_MENU =
            MENUS.register("quarry", () -> IMenuTypeExtension.create(QuarryMenu::new));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("homelink_quarry",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.homelink_quarry"))
                    .icon(() -> QUARRY_I_ITEM.get().getDefaultInstance())
                    .displayItems((parameters, output) -> tabItems().forEach(item -> output.accept(item.get())))
                    .build());

    private QuarryRegistries() {
    }

    public static void register(IEventBus modBus) {
        DATA_COMPONENTS.register(modBus);
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITY_TYPES.register(modBus);
        MENUS.register(modBus);
        TABS.register(modBus);
    }

    public static List<DeferredItem<? extends Item>> tabItems() {
        return List.of(QUARRY_I_ITEM, QUARRY_II_ITEM, QUARRY_III_ITEM, QUARRY_MARKER, MINING_HEAD_I, MINING_HEAD_II, MINING_HEAD_III);
    }

    private static DeferredBlock<QuarryControllerBlock> quarry(String name, QuarryTier tier) {
        return BLOCKS.register(name, () -> new QuarryControllerBlock(tier, BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_ORANGE)
                .strength(3.5F, 6.0F)
                .requiresCorrectToolForDrops()
                .sound(SoundType.METAL)
                .noOcclusion()));
    }
}
