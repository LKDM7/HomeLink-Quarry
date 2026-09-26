package fr.lkdm.homelink.quarry.client;

import fr.lkdm.homelink.quarry.blockentity.QuarryControllerBlockEntity;
import fr.lkdm.homelink.quarry.quarry.QuarryArea;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Samples only the columns under the feet, never loads chunks or changes world blocks. */
final class QuarryGantrySupports {
    private static final Map<QuarryControllerBlockEntity, Cached> CACHE = new WeakHashMap<>();

    record Support(int x, double y, int z) {}
    private record Cached(QuarryArea area, long time, List<Support> supports) {}

    private QuarryGantrySupports() {}

    static List<Support> get(QuarryControllerBlockEntity entity, QuarryArea area) {
        Level level = entity.getLevel();
        if (level == null) return List.of();
        long time = level.getGameTime();
        Cached cached = CACHE.get(entity);
        if (cached != null && cached.area.equals(area) && time >= cached.time && time - cached.time < 20) {
            return cached.supports;
        }
        List<Support> supports = new ArrayList<>(4);
        // Only the two ends of each rail: four corner feet, with no intermediate supports.
        for (int x = area.minX(); ; x = area.maxX()) {
            for (int z : new int[]{area.minZ() - 1, area.maxZ() + 1}) {
                double y = findSurface(level, x, area.startY() + 1, z);
                if (!Double.isNaN(y)) supports.add(new Support(x, y, z));
            }
            if (x == area.maxX()) break;
        }
        List<Support> result = List.copyOf(supports);
        CACHE.put(entity, new Cached(area, time, result));
        return result;
    }

    private static double findSurface(Level level, int x, int top, int z) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, top, z);
        if (!level.hasChunkAt(pos)) return Double.NaN;
        for (int y = Math.min(top, level.getMaxBuildHeight() - 1); y >= level.getMinBuildHeight(); y--) {
            pos.setY(y);
            var shape = level.getBlockState(pos).getCollisionShape(level, pos);
            double surface = Double.NaN;
            // Contact beneath the centre of the post also handles slabs, stairs and fences.
            for (var box : shape.toAabbs()) {
                if (box.minX <= 0.5 && box.maxX >= 0.5 && box.minZ <= 0.5 && box.maxZ >= 0.5) {
                    double height = y + box.maxY;
                    if (height <= top + 1 && (Double.isNaN(surface) || height > surface)) surface = height;
                }
            }
            if (!Double.isNaN(surface)) return surface;
        }
        return Double.NaN;
    }
}
