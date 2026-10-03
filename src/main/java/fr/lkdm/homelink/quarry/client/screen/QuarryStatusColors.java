package fr.lkdm.homelink.quarry.client.screen;

import fr.lkdm.homecore.api.client.ui.HomeLinkStatusTone;
import fr.lkdm.homecore.api.client.ui.HomeLinkTheme;
import fr.lkdm.homelink.quarry.quarry.QuarryStatus;

/** Quarry domain states mapped to shared visual tones; no local palette or rendering. */
public final class QuarryStatusColors {
    private QuarryStatusColors() { }

    public static int color(QuarryStatus status) {
        HomeLinkStatusTone tone = switch (status) {
            case MINING, READY -> HomeLinkStatusTone.ONLINE;
            case IDLE, PAUSED, FINISHED -> HomeLinkStatusTone.NEUTRAL;
            case NO_POWER, NO_HEAD, OUTPUT_FULL -> HomeLinkStatusTone.WARNING;
            case INVALID_AREA, BLOCKED, ERROR -> HomeLinkStatusTone.OFFLINE;
        };
        return HomeLinkTheme.statusColor(tone);
    }
}
