package fr.lkdm.homelink.quarry.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.lkdm.homelink.quarry.blockentity.QuarryControllerBlockEntity;
import fr.lkdm.homelink.quarry.item.QuarryMarkerItem;
import fr.lkdm.homelink.quarry.quarry.AreaCheck;
import fr.lkdm.homelink.quarry.quarry.QuarryArea;
import fr.lkdm.homelink.quarry.quarry.QuarryTier;
import fr.lkdm.homelink.quarry.registry.QuarryRegistries;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import fr.lkdm.homelink.quarry.menu.QuarryMenu;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/** Quarry Controller. FRONT faces the player who placed it; BACK is reserved for the item output port. */
public class QuarryControllerBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<QuarryControllerBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            QuarryTier.CODEC.fieldOf("tier").forGetter(QuarryControllerBlock::tier),
            propertiesCodec()).apply(instance, QuarryControllerBlock::new));

    /** ITEM_OUTPUT port on the back: 0 idle, 1 lighting up, 2 connected to an ITEM_INPUT. */
    public static final IntegerProperty PORT = IntegerProperty.create("port", 0, 2);

    private final QuarryTier tier;

    public QuarryControllerBlock(QuarryTier tier, Properties properties) {
        super(properties);
        this.tier = tier;
        registerDefaultState(stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH).setValue(PORT, 0));
    }

    public QuarryTier tier() {
        return tier;
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PORT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, net.minecraft.world.level.block.Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, net.minecraft.world.level.block.Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    /** A block appearing, changing or disappearing behind the controller re-checks the ITEM_OUTPUT port. */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, net.minecraft.world.level.block.Block neighbor,
                                   BlockPos from, boolean moving) {
        super.neighborChanged(state, level, pos, neighbor, from, moving);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof QuarryControllerBlockEntity entity) entity.markPortDirty();
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof QuarryControllerBlockEntity entity) entity.markPortDirty();
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof QuarryMarkerItem)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof QuarryControllerBlockEntity entity) {
            if (!entity.mayConfigure(player)) {
                player.displayClientMessage(Component.translatable("message.homelink_quarry.denied").withStyle(ChatFormatting.RED), true);
            } else {
                applyMarker(stack, entity, player);
            }
        }
        return ItemInteractionResult.CONSUME;
    }

    /** The placing player owns the quarry (configuration rights, as for HomeLink Farm devices). */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @org.jetbrains.annotations.Nullable net.minecraft.world.entity.LivingEntity placer,
                            ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof Player player && level.getBlockEntity(pos) instanceof QuarryControllerBlockEntity entity) {
            entity.setOwner(player.getUUID(), player.getGameProfile().getName());
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof QuarryControllerBlockEntity entity) {
            if (!entity.mayView(serverPlayer)) {
                serverPlayer.displayClientMessage(Component.translatable("message.homelink_quarry.denied").withStyle(ChatFormatting.RED), true);
                return InteractionResult.CONSUME;
            }
            fr.lkdm.homelink.quarry.network.QuarryPayloads.sendNetworkChoices(serverPlayer, entity);
            serverPlayer.openMenu(new SimpleMenuProvider((id, inventory, owner) -> new QuarryMenu(id, inventory, entity),
                    getName()), buffer -> buffer.writeBlockPos(pos));
        }
        return InteractionResult.CONSUME;
    }

    private static void applyMarker(ItemStack stack, QuarryControllerBlockEntity entity, Player player) {
        GlobalPos a = stack.get(QuarryRegistries.CORNER_A.get());
        GlobalPos b = stack.get(QuarryRegistries.CORNER_B.get());
        Level level = entity.getLevel();
        if (a == null || b == null || level == null) {
            player.displayClientMessage(Component.translatable("message.homelink_quarry.marker_incomplete").withStyle(ChatFormatting.RED), true);
            return;
        }
        if (!a.dimension().equals(level.dimension()) || !b.dimension().equals(level.dimension())) {
            player.displayClientMessage(Component.translatable(AreaCheck.WRONG_DIMENSION.key()).withStyle(ChatFormatting.RED), true);
            return;
        }
        if (!entity.setCorners(a.pos(), b.pos())) {
            player.displayClientMessage(Component.translatable("message.homelink_quarry.locked").withStyle(ChatFormatting.RED), true);
            return;
        }
        AreaCheck check = entity.checkArea();
        QuarryArea area = entity.area().orElseThrow();
        if (check.valid()) {
            player.displayClientMessage(Component.translatable("message.homelink_quarry.area_applied", area.width(), area.length())
                    .withStyle(ChatFormatting.GOLD), true);
        } else {
            int max = entity.tier().maxSide();
            player.displayClientMessage(Component.translatable(check.key()).withStyle(ChatFormatting.RED)
                    .append(Component.literal(" — ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.translatable("message.homelink_quarry.maximum", max, max).withStyle(ChatFormatting.GRAY)), true);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof QuarryControllerBlockEntity entity) {
            entity.dropContents(level, pos);
            entity.destroyed();
            level.updateNeighbourForOutputSignal(pos, this);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    /** Comparator output follows the buffer fill level, like a vanilla container: 0 empty, 15 full. */
    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof QuarryControllerBlockEntity entity
                ? net.neoforged.neoforge.items.ItemHandlerHelper.calcRedstoneFromInventory(entity.buffer()) : 0;
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != QuarryRegistries.QUARRY_CONTROLLER.get()) return null;
        BlockEntityTicker<QuarryControllerBlockEntity> ticker = level.isClientSide
                ? QuarryControllerBlockEntity::clientTick : QuarryControllerBlockEntity::serverTick;
        @SuppressWarnings("unchecked") BlockEntityTicker<T> cast = (BlockEntityTicker<T>) ticker;
        return cast;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new QuarryControllerBlockEntity(pos, state);
    }
}
