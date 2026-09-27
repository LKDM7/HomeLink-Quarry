package fr.lkdm.homelink.quarry.quarry;

import java.util.Locale;

/**
 * Observable quarry state. Waiting states (NO_POWER, NO_HEAD, OUTPUT_FULL, BLOCKED) resume by themselves
 * once the cause disappears; PAUSED only resumes on an explicit RESUME.
 */
public enum QuarryStatus {
    IDLE,
    READY,
    MINING,
    PAUSED,
    /** Not enough HomeLink Energy to drill: the quarry waits, it never mines for free. */
    NO_POWER,
    NO_HEAD,
    OUTPUT_FULL,
    INVALID_AREA,
    BLOCKED,
    FINISHED,
    ERROR;

    private static final QuarryStatus[] VALUES = values();

    public static QuarryStatus byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : ERROR;
    }

    public String key() {
        return "status.homelink_quarry." + name().toLowerCase(Locale.ROOT);
    }
}
