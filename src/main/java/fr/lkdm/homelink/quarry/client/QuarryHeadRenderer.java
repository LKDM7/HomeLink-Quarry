package fr.lkdm.homelink.quarry.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.lkdm.homelink.quarry.HomeLinkQuarry;
import fr.lkdm.homelink.quarry.blockentity.QuarryControllerBlockEntity;
import fr.lkdm.homelink.quarry.quarry.QuarryArea;
import fr.lkdm.homelink.quarry.quarry.QuarryStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.event.ModelEvent;

/**
 * Steel gantry supported outside the excavation, with a travelling bridge and a connected drill.
 * Only the support columns inspect client terrain, cached by QuarryGantrySupports.
 */
public final class QuarryHeadRenderer implements BlockEntityRenderer<QuarryControllerBlockEntity> {
    private static final ModelResourceLocation RAIL = model("gantry_rail");
    private static final ModelResourceLocation FRAME = model("gantry_frame");
    private static final ModelResourceLocation STEEL = model("gantry_shell");
    private static final ModelResourceLocation COPPER = model("gantry_copper");
    private static final ModelResourceLocation SHAFT = model("gantry_shaft");
    private static final ModelResourceLocation[] HOUSINGS = {model("head_housing_i"), model("head_housing_ii"), model("head_housing_iii")};
    private static final ModelResourceLocation[] ROTORS = {model("head_rotor_i"), model("head_rotor_ii"), model("head_rotor_iii")};
    /** Ticks the head takes to glide to a new target. */
    private static final float MOVE_TICKS = 8F;

    private final BlockRenderDispatcher blocks;

    public QuarryHeadRenderer(BlockEntityRendererProvider.Context context) {
        this.blocks = context.getBlockRenderDispatcher();
    }

    private static ModelResourceLocation model(String name) {
        return ModelResourceLocation.standalone(HomeLinkQuarry.id("block/" + name));
    }

    static void registerModels(ModelEvent.RegisterAdditional event) {
        for (var model : new ModelResourceLocation[]{RAIL, FRAME, STEEL, COPPER, SHAFT}) event.register(model);
        for (var model : HOUSINGS) event.register(model);
        for (var model : ROTORS) event.register(model);
    }

    @Override
    public void render(QuarryControllerBlockEntity entity, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        QuarryArea area = entity.area().orElse(null);
        BlockPos target = entity.currentTarget();
        if (area == null || target == null || entity.headLevel() == 0 || !entity.checkAreaClient()) return;
        var level = entity.getLevel();
        if (level == null) return;
        double time = level.getGameTime() + (double) partialTick;
        Vec3 head = HeadMotion.position(entity, Vec3.atLowerCornerOf(target.above()), time, MOVE_TICKS);
        BlockPos origin = entity.getBlockPos();
        double railY = area.startY() + 2;
        boolean drilling = entity.status() == QuarryStatus.MINING;
        double headY = head.y - (drilling ? 0.06 * Math.abs(Math.sin(time * 0.6)) : 0);

        pose.pushPose();
        // Subtract the origin in double precision before entering PoseStack's float matrices.
        // Translating by -worldOrigin and then +worldPosition loses sub-block precision far from spawn.
        // Both tracks and all feet sit outside the mined volume.
        for (var support : QuarryGantrySupports.get(entity, area)) {
            double x = support.x() + 0.5, z = support.z() + 0.5, bottom = support.y();
            box(pose, buffers, level, origin, x - 0.375, bottom, z - 0.375, 0.75, 0.125, 0.75, FRAME);
            box(pose, buffers, level, origin, x - 0.125, bottom + 0.125, z - 0.125,
                    0.25, railY - bottom - 0.125, 0.25, STEEL);
            box(pose, buffers, level, origin, x - 0.25, railY - 0.1875, z - 0.25, 0.5, 0.1875, 0.5, COPPER);
        }
        for (int x = area.minX(); x <= area.maxX(); x++) {
            box(pose, buffers, level, origin, x, railY, area.minZ() - 1, 1, 1, 1, RAIL);
            box(pose, buffers, level, origin, x, railY, area.maxZ() + 1, 1, 1, 1, RAIL);
        }
        // The bridge rests on the shoes: their upper faces must not share its flange's upper plane.
        for (double z : new double[]{area.minZ() - 0.5, area.maxZ() + 1.5}) {
            box(pose, buffers, level, origin, head.x + 0.125, railY + 0.3125, z - 0.3125, 0.75, 0.1875, 0.625, FRAME);
            box(pose, buffers, level, origin, head.x + 0.28125, railY + 0.375, z - 0.375, 0.4375, 0.25, 0.125, COPPER);
        }
        // Reuse the same rail profile, rotated across Z; one segment per block preserves its UV scale.
        for (int z = area.minZ() - 1; z <= area.maxZ() + 1; z++) {
            pose.pushPose();
            pose.translate(head.x - origin.getX(), railY + 0.5 - origin.getY(), z + 1 - origin.getZ());
            pose.mulPose(Axis.YP.rotationDegrees(90));
            drawModel(pose, buffers, RAIL, lightAt(level, head.x + 0.5, railY + 0.5, z + 0.5));
            pose.popPose();
        }
        // The carriage wraps the bridge; its lower bearing meets the telescopic spindle.
        box(pose, buffers, level, origin, head.x + 0.1875, railY + 0.375, head.z + 0.1875, 0.625, 0.125, 0.625, FRAME);
        box(pose, buffers, level, origin, head.x + 0.1875, railY + 0.8125, head.z + 0.1875, 0.625, 0.125, 0.625, STEEL);
        for (double dx : new double[]{0.1875, 0.6875}) {
            box(pose, buffers, level, origin, head.x + dx, railY + 0.5, head.z + 0.1875, 0.125, 0.3125, 0.625, COPPER);
        }
        double shaftBottom = headY + 0.9375;
        double shaftTop = railY + 0.4375;
        box(pose, buffers, level, origin, head.x + 0.40625, shaftBottom, head.z + 0.40625,
                0.1875, shaftTop - shaftBottom, 0.1875, SHAFT);
        box(pose, buffers, level, origin, head.x + 0.3125, railY + 0.1875, head.z + 0.3125, 0.375, 0.25, 0.375, COPPER);

        // Fixed housing stays attached to the spindle; only the cutting assembly rotates.
        int tier = Mth.clamp(entity.headLevel(), 1, 3) - 1;
        // Light of the head's own cell, without the drilling bob: the bob dips under the cell boundary
        // into the block being drilled, whose light is 0, which turned the head black while working.
        int headLight = lightAt(level, head.x + 0.5, head.y + 0.5, head.z + 0.5);
        pose.pushPose();
        pose.translate(head.x - origin.getX(), headY - origin.getY(), head.z - origin.getZ());
        drawModel(pose, buffers, HOUSINGS[tier], headLight);
        pose.translate(0.5, 0, 0.5);
        if (drilling) pose.mulPose(Axis.YP.rotationDegrees((float) (time * 24 % 360)));
        pose.translate(-0.5, 0, -0.5);
        drawModel(pose, buffers, ROTORS[tier], headLight);
        pose.popPose();
        pose.popPose();
    }

    private void box(PoseStack pose, MultiBufferSource buffers, Level level, BlockPos origin,
                     double x, double y, double z, double sx, double sy, double sz, ModelResourceLocation model) {
        if (sx <= 0 || sy <= 0 || sz <= 0) return;
        pose.pushPose();
        pose.translate(x - origin.getX(), y - origin.getY(), z - origin.getZ());
        pose.scale((float) sx, (float) sy, (float) sz);
        drawModel(pose, buffers, model, lightAt(level, x + sx / 2, y + sy / 2, z + sz / 2));
        pose.popPose();
    }

    /**
     * Packed light at a point. A point inside an opaque block (light 0) takes the light of the block above,
     * so parts touching the ground or the block being drilled are not rendered black.
     */
    private static int lightAt(Level level, double x, double y, double z) {
        BlockPos pos = BlockPos.containing(x, y, z);
        if (level.getBlockState(pos).isSolidRender(level, pos)) pos = pos.above();
        return LevelRenderer.getLightColor(level, pos);
    }

    private void drawModel(PoseStack pose, MultiBufferSource buffers, ModelResourceLocation model, int light) {
        blocks.getModelRenderer().renderModel(pose.last(), buffers.getBuffer(RenderType.solid()), null,
                Minecraft.getInstance().getModelManager().getModel(model), 1F, 1F, 1F,
                light, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, RenderType.solid());
    }

    @Override
    public boolean shouldRenderOffScreen(QuarryControllerBlockEntity entity) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 128;
    }

    @Override
    public AABB getRenderBoundingBox(QuarryControllerBlockEntity entity) {
        return entity.area().map(area -> new AABB(area.minX(),
                        entity.getLevel() == null ? area.stopY() : entity.getLevel().getMinBuildHeight(),
                        area.minZ() - 1, area.maxX() + 1, area.startY() + 3, area.maxZ() + 2)
                        .minmax(new AABB(entity.getBlockPos())))
                .orElse(new AABB(entity.getBlockPos()));
    }

    /** Smooth glide of the head between targets, remembered per controller on the client. */
    static final class HeadMotion {
        private static final java.util.Map<QuarryControllerBlockEntity, double[]> STATE = new java.util.WeakHashMap<>();

        static Vec3 position(QuarryControllerBlockEntity entity, Vec3 goal, double time, double duration) {
            // [fromX, fromY, fromZ, goalX, goalY, goalZ, start]
            double[] s = STATE.computeIfAbsent(entity, key -> new double[]{goal.x, goal.y, goal.z,
                    goal.x, goal.y, goal.z, time});
            if (s[3] != goal.x || s[4] != goal.y || s[5] != goal.z) {
                double k = easing((time - s[6]) / duration);
                s[0] = Mth.lerp(k, s[0], s[3]);
                s[1] = Mth.lerp(k, s[1], s[4]);
                s[2] = Mth.lerp(k, s[2], s[5]);
                s[3] = goal.x;
                s[4] = goal.y;
                s[5] = goal.z;
                s[6] = time;
            }
            double eased = easing((time - s[6]) / duration);
            return new Vec3(Mth.lerp(eased, s[0], s[3]), Mth.lerp(eased, s[1], s[4]), Mth.lerp(eased, s[2], s[5]));
        }

        private static double easing(double progress) {
            double k = Mth.clamp(progress, 0, 1);
            return k * k * (3 - 2 * k);
        }
    }
}
