package fr.lkdm.homelink.quarry.quarry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class QuarryEnergyTest {
    @Test void everyHeadPaysExactlyOneBlockOfEnergy() {
        for (MiningHeadTier head : MiningHeadTier.values()) {
            for (long perBlock : new long[] {0, 1, 7, 40, 199, 1000, 12_345}) {
                long paid = 0;
                for (int tick = 1; tick <= head.ticksPerBlock(); tick++) {
                    long step = QuarryEnergy.forDrillTick(tick, head.ticksPerBlock(), perBlock);
                    assertTrue(step >= 0, "negative payment");
                    paid += step;
                }
                assertEquals(perBlock, paid, head + " at " + perBlock + " HE/block");
            }
        }
    }

    @Test void ticksOutsideTheDrillingWindowCostNothing() {
        assertEquals(0, QuarryEnergy.forDrillTick(0, 60, 100));
        assertEquals(0, QuarryEnergy.forDrillTick(61, 60, 100));
        assertEquals(0, QuarryEnergy.forDrillTick(1, 0, 100));
    }

    @Test void energyDecidesRuntimeButNeverSpeed() {
        assertEquals(5, QuarryEnergy.estimatedBlocks(500, 100));
        assertEquals(0, QuarryEnergy.estimatedBlocks(500, 0));
        assertEquals(5L * MiningHeadTier.I.ticksPerBlock(), QuarryEnergy.runtimeTicks(500, 100, MiningHeadTier.I));
        assertEquals(5L * MiningHeadTier.III.ticksPerBlock(), QuarryEnergy.runtimeTicks(500, 100, MiningHeadTier.III));
    }

    @Test void betterHeadsDrillFaster() {
        MiningHeadTier[] heads = MiningHeadTier.values();
        for (int i = 1; i < heads.length; i++) {
            assertTrue(heads[i].ticksPerBlock() < heads[i - 1].ticksPerBlock());
            assertEquals(i + 1, heads[i].level());
        }
    }

    @Test void unknownStatusIdsDecodeAsError() {
        assertEquals(QuarryStatus.ERROR, QuarryStatus.byId(-1));
        assertEquals(QuarryStatus.ERROR, QuarryStatus.byId(QuarryStatus.values().length));
        for (QuarryStatus status : QuarryStatus.values()) assertEquals(status, QuarryStatus.byId(status.ordinal()));
    }
}
