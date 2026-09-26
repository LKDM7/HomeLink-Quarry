package fr.lkdm.homelink.quarry.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.lkdm.homelink.quarry.blockentity.QuarryControllerBlockEntity;
import fr.lkdm.homelink.quarry.config.QuarryConfig;
import fr.lkdm.homelink.quarry.item.QuarryMarkerItem;
import fr.lkdm.homelink.quarry.quarry.QuarryArea;
import fr.lkdm.homelink.quarry.quarry.QuarryStatus;
import fr.lkdm.homelink.quarry.registry.QuarryRegistries;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Client-only, personal preview of quarry volumes. Everything is rebuilt each frame from the few values
 * the server syncs (corners, Stop Y, cursor, status): a handful of lines per quarry, never one shape per block.
 * Valid areas are copper, invalid ones red / dark copper; each line is drawn once depth-tested and once,
 * much fainter, through the terrain so the whole volume reads like a light hologram.
 */
public final class QuarryAreaRenderer {
    public static final int COPPER = 0xD9834B;
    public static final int COPPER_BRIGHT = 0xF6C08A;
    public static final int INVALID = 0xD9604F;
    public static final int INVALID_DARK = 0x8E4A2C;
    public static final int WHITE = 0xE7E5E0;
    public static final int GREY = 0xAFB1AD;
    private static final int LAYER_STEP = 8;
    private static final float THROUGH = 0.22F;
    /** Quarries drawn in the last frame; read by the in-game checks. */
    private static int drawnLastFrame;

    public static int drawnLastFrame() {
        return drawnLastFrame;
    }

    private QuarryAreaRenderer() {
    }

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) return;
        ItemStack marker = heldMarker(client);
        drawnLastFrame = 0;
        if (marker.isEmpty() && QuarryPreview.all().values().stream().noneMatch(QuarryPreview.Options::any)) return;

        Vec3 camera = event.getCamera().getPosition();
        double time = client.level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double maxDistance = QuarryConfig.PREVIEW_RENDER_DISTANCE.get();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = client.renderBuffers().bufferSource();
        // Two complete passes: an immediate buffer source can only fill one render type at a time.
        drawAll(new Pass(pose, buffers.getBuffer(RenderType.lines()), 1F), client, marker, camera, time, maxDistance, true);
        buffers.endBatch(RenderType.lines());
        drawAll(new Pass(pose, buffers.getBuffer(QuarryRenderTypes.LINES_THROUGH), THROUGH), client, marker, camera, time, maxDistance, false);
        buffers.endBatch(QuarryRenderTypes.LINES_THROUGH);
        if (!QuarryPreview.all().isEmpty()) labels(event, client, buffers, camera, maxDistance);
    }

    private static void drawAll(Pass pass, Minecraft client, ItemStack marker, Vec3 camera, double time, double maxDistance, boolean count) {
        PoseStack pose = pass.pose();
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        for (var entry : QuarryPreview.all().entrySet()) {
            GlobalPos key = entry.getKey();
            QuarryPreview.Options options = entry.getValue();
            if (!options.any() || !key.dimension().equals(client.level.dimension())) continue;
            if (!(client.level.getBlockEntity(key.pos()) instanceof QuarryControllerBlockEntity quarry)) continue;
            QuarryArea area = quarry.area().orElse(null);
            if (area == null) continue;
            AABB box = new AABB(area.minX(), area.stopY(), area.minZ(), area.maxX() + 1, area.startY() + 1, area.maxZ() + 1);
            double distance = Math.sqrt(distanceSqr(box, camera));
            if (distance > maxDistance) continue;
            float fade = (float) Math.clamp((maxDistance - distance) / (maxDistance * 0.25), 0, 1);
            draw(pass, quarry, area, box, options, fade, time);
            if (count) drawnLastFrame++;
        }
        if (!marker.isEmpty()) drawMarker(pass, client, marker, camera, maxDistance);
        pose.popPose();
    }

    private static void draw(Pass pass, QuarryControllerBlockEntity quarry, QuarryArea area, AABB box, QuarryPreview.Options options,
                             float fade, double time) {
        boolean valid = quarry.checkAreaClient();
        int edge = valid ? COPPER : INVALID;
        if (options.area) {
            pass.box(box, edge, 0.9F * fade);
            pass.corners(box.inflate(0.02), valid ? WHITE : INVALID_DARK, fade);
        }
        if (options.layers) {
            for (int y = area.startY() + 1 - LAYER_STEP; y > area.stopY(); y -= LAYER_STEP) {
                pass.rectangle(box.minX, y, box.minZ, box.maxX, box.maxZ, GREY, 0.45F * fade);
            }
        }
        BlockPos target = quarry.currentTarget();
        if (options.progress && valid && target != null && activeJob(quarry.status())) {
            int layerY = target.getY();
            // Finished volume above the current layer: dim grey.
            if (layerY < area.startY()) pass.box(new AABB(box.minX, layerY + 1, box.minZ, box.maxX, box.maxY, box.maxZ), GREY, 0.35F * fade);
            // Current layer: brighter copper, pulsing gently.
            float pulse = 0.65F + 0.25F * (float) Math.sin(time * 0.15);
            pass.rectangle(box.minX, layerY + 1, box.minZ, box.maxX, box.maxZ, COPPER_BRIGHT, pulse * fade);
            pass.rectangle(box.minX, layerY, box.minZ, box.maxX, box.maxZ, COPPER_BRIGHT, pulse * 0.6F * fade);
            // Rows of the current layer already processed: a line across at the working row.
            pass.line(box.minX, layerY + 1.01, target.getZ(), box.maxX, layerY + 1.01, target.getZ(), WHITE, 0.5F * fade);
        }
        if (target != null && valid && activeJob(quarry.status()) && (options.progress || options.area)) {
            float glow = quarry.status() == QuarryStatus.MINING ? 0.8F + 0.2F * (float) Math.sin(time * 0.4) : 0.7F;
            pass.box(new AABB(target).inflate(0.015), COPPER_BRIGHT, glow * fade);
            pass.corners(new AABB(target).inflate(0.04), WHITE, fade);
        }
    }

    /** A job exists (started, maybe waiting or paused): only then is there a current target to show. */
    private static boolean activeJob(QuarryStatus status) {
        return switch (status) {
            case MINING, PAUSED, NO_FUEL, NO_HEAD, OUTPUT_FULL, BLOCKED -> true;
            default -> false;
        };
    }

    /** Pending selection of a held Quarry Marker, in white. */
    private static void drawMarker(Pass pass, Minecraft client, ItemStack marker, Vec3 camera, double maxDistance) {
        GlobalPos a = marker.get(QuarryRegistries.CORNER_A.get());
        GlobalPos b = marker.get(QuarryRegistries.CORNER_B.get());
        if (a == null || !a.dimension().equals(client.level.dimension())) return;
        pass.corners(new AABB(a.pos()).inflate(0.02), WHITE, 1F);
        if (b == null || !b.dimension().equals(client.level.dimension())) return;
        BlockPos min = BlockPos.min(a.pos(), b.pos());
        BlockPos max = BlockPos.max(a.pos(), b.pos());
        AABB box = new AABB(min.getX(), min.getY(), min.getZ(), max.getX() + 1, max.getY() + 1, max.getZ() + 1);
        if (Math.sqrt(distanceSqr(box, camera)) > maxDistance) return;
        pass.corners(new AABB(b.pos()).inflate(0.02), WHITE, 1F);
        pass.box(box.inflate(0.01), WHITE, 0.6F);
    }

    /** Y labels on the corner of each layer plane, facing the camera. */
    private static void labels(RenderLevelStageEvent event, Minecraft client, MultiBufferSource.BufferSource buffers, Vec3 camera, double maxDistance) {
        PoseStack pose = event.getPoseStack();
        Font font = client.font;
        for (var entry : QuarryPreview.all().entrySet()) {
            QuarryPreview.Options options = entry.getValue();
            if (!options.layers || !entry.getKey().dimension().equals(client.level.dimension())) continue;
            if (!(client.level.getBlockEntity(entry.getKey().pos()) instanceof QuarryControllerBlockEntity quarry)) continue;
            QuarryArea area = quarry.area().orElse(null);
            if (area == null) continue;
            for (int y = area.startY() + 1; y > area.stopY(); y -= LAYER_STEP) {
                Vec3 at = new Vec3(area.minX() - 0.3, y + 0.15, area.minZ() - 0.3);
                if (at.distanceTo(camera) > maxDistance) continue;
                label(pose, font, buffers, event, camera, at, "Y" + (y - 1));
            }
            label(pose, font, buffers, event, camera, new Vec3(area.minX() - 0.3, area.stopY() + 0.15, area.minZ() - 0.3), "Y" + area.stopY());
        }
        buffers.endBatch();
    }

    private static void label(PoseStack pose, Font font, MultiBufferSource buffers, RenderLevelStageEvent event, Vec3 camera, Vec3 at, String text) {
        pose.pushPose();
        pose.translate(at.x - camera.x, at.y - camera.y, at.z - camera.z);
        pose.mulPose(event.getCamera().rotation());
        pose.scale(0.045F, -0.045F, 0.045F);
        float x = -font.width(text) / 2F;
        font.drawInBatch(text, x, 0, 0xFFE7E5E0, false, pose.last().pose(), buffers, Font.DisplayMode.SEE_THROUGH,
                0x55252729, LightTexture.FULL_BRIGHT);
        pose.popPose();
    }

    private static ItemStack heldMarker(Minecraft client) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = client.player.getItemInHand(hand);
            if (stack.getItem() instanceof QuarryMarkerItem) return stack;
        }
        return ItemStack.EMPTY;
    }

    private static double distanceSqr(AABB box, Vec3 point) {
        double dx = Math.max(0, Math.max(box.minX - point.x, point.x - box.maxX));
        double dy = Math.max(0, Math.max(box.minY - point.y, point.y - box.maxY));
        double dz = Math.max(0, Math.max(box.minZ - point.z, point.z - box.maxZ));
        return dx * dx + dy * dy + dz * dz;
    }

    /** One drawing pass: depth-tested at full opacity, or through the terrain at {@code scale} opacity. */
    private record Pass(PoseStack pose, VertexConsumer out, float scale) {
        void line(double x, double y, double z, double endX, double endY, double endZ, int color, float alpha) {
            vertexLine(out, x, y, z, endX, endY, endZ, color, alpha * scale);
        }

        void rectangle(double minX, double y, double minZ, double maxX, double maxZ, int color, float alpha) {
            line(minX, y, minZ, maxX, y, minZ, color, alpha);
            line(maxX, y, minZ, maxX, y, maxZ, color, alpha);
            line(maxX, y, maxZ, minX, y, maxZ, color, alpha);
            line(minX, y, maxZ, minX, y, minZ, color, alpha);
        }

        /** The 12 edges of a box, including the four vertical corner posts. */
        void box(AABB box, int color, float alpha) {
            rectangle(box.minX, box.minY, box.minZ, box.maxX, box.maxZ, color, alpha);
            rectangle(box.minX, box.maxY, box.minZ, box.maxX, box.maxZ, color, alpha);
            line(box.minX, box.minY, box.minZ, box.minX, box.maxY, box.minZ, color, alpha);
            line(box.maxX, box.minY, box.minZ, box.maxX, box.maxY, box.minZ, color, alpha);
            line(box.minX, box.minY, box.maxZ, box.minX, box.maxY, box.maxZ, color, alpha);
            line(box.maxX, box.minY, box.maxZ, box.maxX, box.maxY, box.maxZ, color, alpha);
        }

        /** Short L-shaped corner marks, as in the HomeLink Farm field guides. */
        void corners(AABB box, int color, float alpha) {
            double sx = Math.min(0.65, box.getXsize() / 3), sy = Math.min(0.8, box.getYsize() / 3), sz = Math.min(0.65, box.getZsize() / 3);
            for (int ix = 0; ix < 2; ix++) for (int iy = 0; iy < 2; iy++) for (int iz = 0; iz < 2; iz++) {
                double x = ix == 0 ? box.minX : box.maxX, y = iy == 0 ? box.minY : box.maxY, z = iz == 0 ? box.minZ : box.maxZ;
                line(x, y, z, x + (ix == 0 ? sx : -sx), y, z, color, alpha);
                line(x, y, z, x, y + (iy == 0 ? sy : -sy), z, color, alpha);
                line(x, y, z, x, y, z + (iz == 0 ? sz : -sz), color, alpha);
            }
        }

        private void vertexLine(VertexConsumer out, double x, double y, double z, double endX, double endY, double endZ, int color, float alpha) {
            double length = Math.sqrt((endX - x) * (endX - x) + (endY - y) * (endY - y) + (endZ - z) * (endZ - z));
            if (length < 1.0E-5 || alpha <= 0) return;
            float nx = (float) ((endX - x) / length), ny = (float) ((endY - y) / length), nz = (float) ((endZ - z) / length);
            int a = (int) (255 * Math.clamp(alpha, 0, 1));
            int r = color >> 16 & 255, g = color >> 8 & 255, b = color & 255;
            out.addVertex(pose.last().pose(), (float) x, (float) y, (float) z).setColor(r, g, b, a).setNormal(pose.last(), nx, ny, nz);
            out.addVertex(pose.last().pose(), (float) endX, (float) endY, (float) endZ).setColor(r, g, b, a).setNormal(pose.last(), nx, ny, nz);
        }
    }
}
