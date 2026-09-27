package fr.lkdm.homelink.quarry.quarry;

import fr.lkdm.homelink.quarry.config.QuarryConfig;

/**
 * Energy rules. A quarry runs on HomeLink Energy (HE) only, taken from any energy network through the
 * HomeCore energy capability. HE is only used while a head drills: it sets how long a quarry runs, never
 * how fast it mines. A block costs the same HE whatever the head; a faster head just asks for it faster.
 */
public final class QuarryEnergy {
    /** Blocks of drilling the internal buffer holds, so short gaps in supply go unnoticed. */
    public static final int BUFFER_BLOCKS = 50;

    private QuarryEnergy() {
    }

    /** HE to drill one block, from the server config (0 before it is loaded). */
    public static long perBlock() {
        return QuarryConfig.SERVER_SPEC.isLoaded() ? QuarryConfig.ENERGY_PER_BLOCK.get() : 0;
    }

    /** Size of the internal buffer in HE. */
    public static long capacity() {
        return perBlock() * BUFFER_BLOCKS;
    }

    /**
     * Energy used on one drilling tick so that a block costs exactly {@code perBlock} over
     * {@code required} ticks: {@code floor(k * perBlock / required) - floor((k - 1) * perBlock / required)}.
     *
     * @param tick drilling tick being paid, from 1 to {@code required}
     * @param required ticks the head needs for one block
     * @param perBlock energy for one block
     * @return energy for that tick
     */
    public static long forDrillTick(int tick, int required, long perBlock) {
        if (tick <= 0 || required <= 0 || tick > required) return 0;
        return perBlock * tick / required - perBlock * (tick - 1) / required;
    }

    /** Blocks that {@code stored} HE can still drill. */
    public static long estimatedBlocks(long stored, long perBlock) {
        return perBlock <= 0 ? 0 : stored / perBlock;
    }

    /** Drilling ticks that {@code stored} HE lasts with the given head; the rate itself never depends on energy. */
    public static long runtimeTicks(long stored, long perBlock, MiningHeadTier head) {
        return perBlock <= 0 ? 0 : stored * head.ticksPerBlock() / perBlock;
    }
}
