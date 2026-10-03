package fr.lkdm.homelink.quarry.client.screen;

import fr.lkdm.homecore.api.client.ui.HomeLinkTheme;

/**
 * Window geometry shared with the in-game checks. Control rectangles are {x, y, width, height},
 * relative to the window: shared 18-pixel controls on 21-pixel rows.
 */
public final class QuarryScreenLayout {
    public static final int WIDTH = 270;
    public static final int HEIGHT = 234;
    public static final int BUTTON_HEIGHT = HomeLinkTheme.CONTROL_HEIGHT;
    public static final int BUTTON_ROW = 21;
    public static final int BOTTOM = HEIGHT - BUTTON_HEIGHT - 6;
    public static final int ROW_2 = BOTTOM - BUTTON_ROW;
    public static final int ROW_1 = BOTTOM - 2 * BUTTON_ROW;

    // STATUS view
    public static final int[] PRIMARY = {10, ROW_2, 82, BUTTON_HEIGHT};
    public static final int[] STOP = {94, ROW_2, 82, BUTTON_HEIGHT};
    public static final int[] AREA = {178, ROW_2, 82, BUTTON_HEIGHT};
    public static final int[] OUTPUT = {10, BOTTOM, 123, BUTTON_HEIGHT};
    public static final int[] NETWORK = {137, BOTTOM, 123, BUTTON_HEIGHT};

    // AREA view
    public static final int[] STOP_Y_DOWN = {10, ROW_1, 60, BUTTON_HEIGHT};
    public static final int[] STOP_Y_UP = {72, ROW_1, 60, BUTTON_HEIGHT};
    public static final int[] STOP_Y_MIN = {134, ROW_1, 60, BUTTON_HEIGHT};
    public static final int[] PREVIEW = {196, ROW_1, 64, BUTTON_HEIGHT};
    public static final int[] SHOW_AREA = {10, ROW_2, 82, BUTTON_HEIGHT};
    public static final int[] SHOW_LAYERS = {94, ROW_2, 82, BUTTON_HEIGHT};
    public static final int[] SHOW_PROGRESS = {178, ROW_2, 82, BUTTON_HEIGHT};
    public static final int[] BACK = {10, BOTTOM, 250, BUTTON_HEIGHT};

    private QuarryScreenLayout() {
    }
}
