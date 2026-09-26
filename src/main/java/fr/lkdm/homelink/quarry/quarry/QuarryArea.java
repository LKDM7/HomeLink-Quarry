package fr.lkdm.homelink.quarry.quarry;

import net.minecraft.core.BlockPos;

/**
 * Immutable mined volume. Positions are visited X first, then Z, then the next layer down,
 * so a single cursor fully describes the progress and nothing is ever scanned in bulk.
 */
public record QuarryArea(int minX, int minZ, int maxX, int maxZ, int startY, int stopY) {
    public static QuarryArea of(BlockPos cornerA, BlockPos cornerB, int stopY) {
        return new QuarryArea(Math.min(cornerA.getX(), cornerB.getX()), Math.min(cornerA.getZ(), cornerB.getZ()),
                Math.max(cornerA.getX(), cornerB.getX()), Math.max(cornerA.getZ(), cornerB.getZ()),
                Math.max(cornerA.getY(), cornerB.getY()), stopY);
    }

    public int width() {
        return maxX - minX + 1;
    }

    public int length() {
        return maxZ - minZ + 1;
    }

    /** Number of layers from Start Y down to Stop Y, both included; zero when Stop Y is above Start Y. */
    public int layers() {
        return Math.max(0, startY - stopY + 1);
    }

    public int layerSize() {
        return width() * length();
    }

    public long totalPositions() {
        return (long) layerSize() * layers();
    }

    public int layerY(long cursor) {
        return startY - (int) (cursor / layerSize());
    }

    /** Position of a cursor in [0, totalPositions). */
    public BlockPos target(long cursor) {
        int inLayer = (int) (cursor % layerSize());
        return new BlockPos(minX + inLayer % width(), layerY(cursor), minZ + inLayer / width());
    }

    public boolean contains(BlockPos pos) {
        return pos.getX() >= minX && pos.getX() <= maxX && pos.getZ() >= minZ && pos.getZ() <= maxZ
                && pos.getY() <= startY && pos.getY() >= stopY;
    }

    /** Horizontal (Chebyshev) distance from a position to the closest column of the area. */
    public int horizontalDistance(BlockPos pos) {
        int dx = Math.max(0, Math.max(minX - pos.getX(), pos.getX() - maxX));
        int dz = Math.max(0, Math.max(minZ - pos.getZ(), pos.getZ() - maxZ));
        return Math.max(dx, dz);
    }
}
