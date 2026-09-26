package fr.lkdm.homelink.quarry.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Invisible light carried by a working Mining Head (torch level). It behaves like air and removes
 * itself a few seconds after its quarry stops renewing it, so a stopped, unloaded or broken quarry
 * never leaves light behind.
 */
public class DrillLightBlock extends Block {
    public static final MapCodec<DrillLightBlock> CODEC = simpleCodec(DrillLightBlock::new);
    public static final int LIGHT = 14;
    /** Seconds without renewal before the light disappears. */
    public static final IntegerProperty AGE = IntegerProperty.create("age", 0, 3);
    private static final int CHECK_TICKS = 20;

    public DrillLightBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AGE, 0));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        if (!old.is(this)) level.scheduleTick(pos, this, CHECK_TICKS);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int age = state.getValue(AGE);
        if (age >= 3) {
            level.removeBlock(pos, false);
            return;
        }
        level.setBlock(pos, state.setValue(AGE, age + 1), Block.UPDATE_CLIENTS);
        level.scheduleTick(pos, this, CHECK_TICKS);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }
}
