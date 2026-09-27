package fr.lkdm.homelink.quarry.client.screen;

import static fr.lkdm.homelink.quarry.client.screen.QuarryScreenLayout.BUTTON_HEIGHT;

import fr.lkdm.homelink.quarry.blockentity.QuarryControllerBlockEntity;
import fr.lkdm.homelink.quarry.client.QuarryPreview;
import fr.lkdm.homelink.quarry.menu.QuarryMenu;
import fr.lkdm.homelink.quarry.network.QuarryPayloads;
import fr.lkdm.homelink.quarry.quarry.AreaCheck;
import fr.lkdm.homelink.quarry.quarry.MiningHeadTier;
import fr.lkdm.homelink.quarry.quarry.QuarryStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * Quarry Controller screen in the HomeLink Dashboard style of HomeLink Farm: header with status light,
 * rename field, read-only status lines in a recessed panel and command buttons at the bottom.
 * Views: STATUS, AREA (depth and preview options), OUTPUT (head, energy, buffer) and the help page.
 * Values are for display only; the server validates every command.
 */
public class QuarryScreen extends AbstractContainerScreen<QuarryMenu> {
    protected static final int TEXT = QuarryTheme.TEXT;
    protected static final int LABEL = QuarryTheme.MUTED;
    protected static final int GOOD = QuarryTheme.ONLINE;
    protected static final int WARN = QuarryTheme.WARNING;
    protected static final int BAD = QuarryTheme.OFFLINE;
    private static final int HEADER_HEIGHT = 24;
    private static final int LINE_HEIGHT = 10;
    private static final int PANEL_TOP = 56;
    private static final int LINES_TOP = PANEL_TOP + 4;
    private static final int VALUE_X = 110;

    public enum View { STATUS, AREA, OUTPUT }

    /** One status line; {@code bar} in 0..1 draws a gauge, negative for none. */
    protected record Line(Component label, Component value, int color, float bar) {
    }

    private View view = View.STATUS;
    private boolean helpOpen;
    private QuarryHelpView help;
    private Button helpUp, helpDown;
    private EditBox nameBox;
    private QuarryStatus shownStatus;
    private boolean shownEditable;
    private Button networkButton;

    public QuarryScreen(QuarryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = QuarryScreenLayout.WIDTH;
        imageHeight = QuarryScreenLayout.HEIGHT;
    }

    public View view() {
        return view;
    }

    public boolean isHelpOpen() {
        return helpOpen;
    }

    @Override
    protected void init() {
        String draft = nameBox == null ? null : nameBox.getValue();
        boolean editingName = nameBox != null && nameBox.isFocused();
        super.init();
        nameBox = null;
        networkButton = null;
        shownStatus = menu.status();
        shownEditable = editable();
        menu.setSlotsVisible(view == View.OUTPUT && !helpOpen);
        QuarryButton helpButton = button(Component.translatable("screen.homelink_quarry.help.button"), imageWidth - 30, 3, 20, this::toggleHelp);
        helpButton.accentWhen(() -> helpOpen);
        helpButton.setTooltip(Tooltip.create(Component.translatable("screen.homelink_quarry.help.tooltip")));
        if (helpOpen) {
            int bottom = QuarryScreenLayout.BOTTOM;
            help = new QuarryHelpView(font, Component.translatable("screen.homelink_quarry.help.content"), leftPos + 10, topPos + 43,
                    imageWidth - 20, bottom - 50, help == null ? 0 : help.offset());
            helpUp = button(Component.translatable("screen.homelink_quarry.help.up"), 10, bottom, 30, () -> { help.scroll(-help.visibleLines()); refreshHelp(); });
            helpDown = button(Component.translatable("screen.homelink_quarry.help.down"), 44, bottom, 30, () -> { help.scroll(help.visibleLines()); refreshHelp(); });
            button(Component.translatable("screen.homelink_quarry.help.back"), 82, bottom, 178, this::toggleHelp);
            refreshHelp();
            return;
        }
        if (view != View.OUTPUT) {
            nameBox = new EditBox(font, leftPos + 10, topPos + 30, 170, 16, Component.translatable("screen.homelink_quarry.name"));
            nameBox.setMaxLength(QuarryPayloads.MAX_NAME_LENGTH);
            if (draft != null) nameBox.setValue(draft);
            else device().ifPresent(device -> nameBox.setValue(device.customName()));
            addRenderableWidget(nameBox);
            if (editingName) setFocused(nameBox);
            button(Component.translatable("screen.homelink_quarry.rename"), 184, 29, 76, this::sendRename);
        }
        switch (view) {
            case STATUS -> statusWidgets();
            case AREA -> areaWidgets();
            case OUTPUT -> button(Component.translatable("screen.homelink_quarry.back"), QuarryScreenLayout.BACK, () -> switchView(View.STATUS));
        }
    }

    private void statusWidgets() {
        QuarryStatus status = menu.status();
        boolean paused = status == QuarryStatus.PAUSED;
        boolean working = working(status);
        Button primary = button(Component.translatable(paused ? "screen.homelink_quarry.resume"
                : working ? "screen.homelink_quarry.pause" : "screen.homelink_quarry.start"), QuarryScreenLayout.PRIMARY, this::pressPrimary);
        primary.active = paused || working || status == QuarryStatus.READY || status == QuarryStatus.FINISHED;
        if (!primary.active) primary.setTooltip(Tooltip.create(Component.translatable("screen.homelink_quarry.cannot_start", Component.translatable(status.key()))));
        Button stop = button(Component.translatable("screen.homelink_quarry.stop"), QuarryScreenLayout.STOP, () -> send(QuarryMenu.BUTTON_STOP));
        stop.active = paused || working;
        stop.setTooltip(Tooltip.create(Component.translatable("screen.homelink_quarry.stop_tooltip")));
        button(Component.translatable("screen.homelink_quarry.area_view"), QuarryScreenLayout.AREA, () -> switchView(View.AREA));
        button(Component.translatable("screen.homelink_quarry.output_view"), QuarryScreenLayout.OUTPUT, () -> switchView(View.OUTPUT));
        networkButton = button(networkLabel(), QuarryScreenLayout.NETWORK, this::cycleNetwork);
        networkButton.setTooltip(Tooltip.create(Component.translatable("screen.homelink_quarry.network_tooltip")));
    }

    private Component networkLabel() {
        String name = device().filter(QuarryControllerBlockEntity::hasHomeNetwork).map(QuarryControllerBlockEntity::homeNetworkName).orElse(null);
        return Component.translatable("screen.homelink_quarry.network", name == null
                ? Component.translatable("screen.homelink_quarry.network_none") : Component.literal(name));
    }

    /** Cycles through the networks the player may manage (sent by the server) and "none"; the server re-checks. */
    private void cycleNetwork() {
        var choices = fr.lkdm.homelink.quarry.client.QuarryClientData.choices(menu.position());
        String current = device().filter(QuarryControllerBlockEntity::hasHomeNetwork).map(QuarryControllerBlockEntity::homeNetworkName).orElse(null);
        if (choices.isEmpty() && current == null) {
            if (minecraft != null && minecraft.player != null)
                minecraft.player.displayClientMessage(Component.translatable("message.homelink_quarry.network.no_choices"), true);
            return;
        }
        int index = -1;
        for (int i = 0; i < choices.size(); i++) if (current != null && choices.get(i).name().equals(current)) index = i;
        int next = index + 1;
        Optional<java.util.UUID> target = next < choices.size() ? Optional.of(choices.get(next).id()) : Optional.empty();
        PacketDistributor.sendToServer(new QuarryPayloads.Bind(menu.position(), target));
    }

    private void areaWidgets() {
        boolean editable = editable();
        Button down = button(Component.translatable("screen.homelink_quarry.stop_y_down"), QuarryScreenLayout.STOP_Y_DOWN,
                () -> send(Screen.hasShiftDown() ? QuarryMenu.BUTTON_STOP_Y_DOWN_10 : QuarryMenu.BUTTON_STOP_Y_DOWN));
        Button up = button(Component.translatable("screen.homelink_quarry.stop_y_up"), QuarryScreenLayout.STOP_Y_UP,
                () -> send(Screen.hasShiftDown() ? QuarryMenu.BUTTON_STOP_Y_UP_10 : QuarryMenu.BUTTON_STOP_Y_UP));
        Button min = button(Component.translatable("screen.homelink_quarry.stop_y_bottom"), QuarryScreenLayout.STOP_Y_MIN,
                () -> send(QuarryMenu.BUTTON_STOP_Y_MIN));
        down.active = editable;
        up.active = editable && menu.stopY() < menu.startY();
        min.active = editable;
        Component lockedHint = Component.translatable(menu.hasArea() ? "screen.homelink_quarry.locked_hint" : "screen.homelink_quarry.no_area_hint");
        for (Button button : List.of(down, up)) button.setTooltip(Tooltip.create(editable
                ? Component.translatable("screen.homelink_quarry.stop_y_hint") : lockedHint));
        min.setTooltip(Tooltip.create(editable ? Component.translatable("screen.homelink_quarry.stop_y_min") : lockedHint));

        QuarryPreview.Options options = options();
        QuarryButton preview = button(Component.translatable(options.any() ? "screen.homelink_quarry.preview_hide" : "screen.homelink_quarry.preview"),
                QuarryScreenLayout.PREVIEW, this::pressPreview);
        preview.active = menu.hasArea();
        preview.setTooltip(Tooltip.create(Component.translatable("screen.homelink_quarry.preview_tooltip")));
        toggle("screen.homelink_quarry.show_area", QuarryScreenLayout.SHOW_AREA, () -> options.area, value -> options.area = value);
        toggle("screen.homelink_quarry.show_layers", QuarryScreenLayout.SHOW_LAYERS, () -> options.layers, value -> options.layers = value);
        toggle("screen.homelink_quarry.show_progress", QuarryScreenLayout.SHOW_PROGRESS, () -> options.progress, value -> options.progress = value);
        button(Component.translatable("screen.homelink_quarry.back"), QuarryScreenLayout.BACK, () -> switchView(View.STATUS));
    }

    /** ON/OFF option: gold while ON, with the state written in the label. */
    private void toggle(String key, int[] bounds, java.util.function.BooleanSupplier get, java.util.function.Consumer<Boolean> set) {
        QuarryButton[] holder = new QuarryButton[1];
        holder[0] = button(toggleLabel(key, get.getAsBoolean()), bounds, () -> {
            set.accept(!get.getAsBoolean());
            holder[0].setMessage(toggleLabel(key, get.getAsBoolean()));
            rebuildWidgets();
        });
        holder[0].accentWhen(get);
        holder[0].active = menu.hasArea();
    }

    private static Component toggleLabel(String key, boolean on) {
        return Component.translatable(key, Component.translatable(on ? "screen.homelink_quarry.on" : "screen.homelink_quarry.off"));
    }

    private QuarryPreview.Options options() {
        return QuarryPreview.get(minecraft.level.dimension(), menu.position());
    }

    /** PREVIEW: shows the area outline and closes the screen so the player can look at it; again hides it. */
    private void pressPreview() {
        QuarryPreview.Options options = options();
        if (options.any()) {
            options.area = options.layers = options.progress = false;
            rebuildWidgets();
            return;
        }
        options.area = true;
        onClose();
    }

    private void pressPrimary() {
        QuarryStatus status = menu.status();
        if (status == QuarryStatus.PAUSED) send(QuarryMenu.BUTTON_RESUME);
        else if (working(status)) send(QuarryMenu.BUTTON_PAUSE);
        else send(QuarryMenu.BUTTON_START);
    }

    private static boolean working(QuarryStatus status) {
        return switch (status) {
            case MINING, NO_POWER, OUTPUT_FULL, BLOCKED -> true;
            default -> false;
        };
    }

    private boolean editable() {
        return menu.hasArea() && !menu.locked();
    }

    protected void send(int id) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    private void sendRename() {
        PacketDistributor.sendToServer(new QuarryPayloads.Rename(menu.position(), nameBox.getValue()));
    }

    private void switchView(View next) {
        view = next;
        rebuildWidgets();
    }

    private void toggleHelp() {
        helpOpen = !helpOpen;
        rebuildWidgets();
    }

    private void refreshHelp() {
        helpUp.active = help.offset() > 0;
        helpDown.active = help.offset() < help.maxOffset();
    }

    private QuarryButton button(Component label, int[] bounds, Runnable action) {
        return button(label, bounds[0], bounds[1], bounds[2], action);
    }

    private QuarryButton button(Component label, int x, int y, int width, Runnable action) {
        return addRenderableWidget((QuarryButton) QuarryButton.builder(label, pressed -> action.run())
                .bounds(leftPos + x, topPos + y, width, BUTTON_HEIGHT).build());
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (helpOpen || view == View.OUTPUT) return;
        if (networkButton != null && !networkLabel().equals(networkButton.getMessage())) networkButton.setMessage(networkLabel());
        if (menu.status() != shownStatus || editable() != shownEditable) rebuildWidgets();
    }

    private Optional<QuarryControllerBlockEntity> device() {
        if (minecraft == null || minecraft.level == null) return Optional.empty();
        return minecraft.level.getBlockEntity(menu.position()) instanceof QuarryControllerBlockEntity entity ? Optional.of(entity) : Optional.empty();
    }

    // ---- Content --------------------------------------------------------------------------------

    private List<Line> lines() {
        List<Line> lines = new ArrayList<>();
        if (view == View.STATUS) statusLines(lines);
        else if (view == View.AREA) areaLines(lines);
        return lines;
    }

    private void statusLines(List<Line> lines) {
        boolean area = menu.hasArea();
        lines.add(line("screen.homelink_quarry.level", Component.translatable("screen.homelink_quarry.level_value",
                roman(menu.maxSide()), menu.maxSide(), menu.maxSide()), TEXT));
        lines.add(line("screen.homelink_quarry.area", areaValue(), area && !menu.areaCheck().valid() ? BAD : TEXT));
        lines.add(line("screen.homelink_quarry.depth", area ? Component.translatable("screen.homelink_quarry.depth_value", menu.startY(), menu.stopY())
                : dash(), TEXT));
        lines.add(line("screen.homelink_quarry.current_layer", area && menu.status() != QuarryStatus.FINISHED
                ? Component.literal("Y " + menu.layerY()) : dash(), TEXT));
        lines.add(new Line(Component.translatable("screen.homelink_quarry.progress"), percent((float) menu.progress()),
                menu.status() == QuarryStatus.FINISHED ? GOOD : TEXT, (float) menu.progress()));
        lines.add(line("screen.homelink_quarry.blocks_mined", number(menu.blocksMined()), TEXT));
        lines.add(line("screen.homelink_quarry.blocks_remaining", number(menu.positionsRemaining()), TEXT));
        int head = menu.headLevel();
        lines.add(head > 0
                ? line("screen.homelink_quarry.head", Component.translatable("screen.homelink_quarry.head_value",
                        MiningHeadTier.values()[head - 1].roman(), MiningHeadTier.values()[head - 1].secondsPerBlock()), TEXT)
                : line("screen.homelink_quarry.head", Component.translatable("screen.homelink_quarry.none"), WARN));
        float energy = menu.energyPercent() / 100F;
        lines.add(new Line(Component.translatable("screen.homelink_quarry.energy"), percent(energy),
                menu.energyPercent() == 0 ? BAD : menu.energyPercent() <= 15 ? WARN : TEXT, energy));
        Component runtime = runtime(menu.runtimeSeconds());
        if (head > 0 && menu.runtimeSeconds() > 0)
            runtime = Component.translatable("screen.homelink_quarry.runtime_value", runtime, number(menu.estimatedBlocks()));
        lines.add(line("screen.homelink_quarry.runtime", runtime, menu.runtimeSeconds() == 0 ? BAD : TEXT));
        float used = menu.bufferPercent() / 100F;
        lines.add(new Line(Component.translatable("screen.homelink_quarry.output"),
                Component.translatable("screen.homelink_quarry.output_value", menu.bufferUsed()),
                menu.bufferUsed() >= QuarryControllerBlockEntity.BUFFER_SLOTS ? BAD : TEXT, used));
        lines.add(line("screen.homelink_quarry.storage_output", Component.translatable(menu.outputConnected()
                ? "screen.homelink_quarry.connected" : "screen.homelink_quarry.disconnected"), menu.outputConnected() ? GOOD : LABEL));
    }

    private void areaLines(List<Line> lines) {
        boolean area = menu.hasArea();
        AreaCheck check = menu.areaCheck();
        lines.add(line("screen.homelink_quarry.area", areaValue(), area && !check.valid() ? BAD : TEXT));
        Component validation = !area ? Component.translatable("screen.homelink_quarry.no_area")
                : check == AreaCheck.TOO_LARGE ? Component.translatable(check.key()).copy().append(" · ")
                        .append(Component.translatable("message.homelink_quarry.maximum", menu.maxSide(), menu.maxSide()))
                : Component.translatable(check.key());
        lines.add(line("screen.homelink_quarry.validation", validation, !area ? WARN : check.valid() ? GOOD : BAD));
        lines.add(line("screen.homelink_quarry.start_y", area ? Component.literal("Y " + menu.startY()) : dash(), TEXT));
        lines.add(line("screen.homelink_quarry.stop_y", area ? Component.literal("Y " + menu.stopY()) : dash(), editable() ? QuarryTheme.ACCENT : TEXT));
        lines.add(line("screen.homelink_quarry.layers", area ? number(Math.max(0, menu.startY() - menu.stopY() + 1)) : dash(), TEXT));
        lines.add(line("screen.homelink_quarry.current_layer", area && menu.status() != QuarryStatus.FINISHED
                ? Component.literal("Y " + menu.layerY()) : dash(), TEXT));
        BlockPos target = device().map(QuarryControllerBlockEntity::currentTarget).orElse(null);
        lines.add(line("screen.homelink_quarry.current_target", target == null ? dash()
                : Component.literal(target.getX() + " " + target.getY() + " " + target.getZ()), TEXT));
        QuarryPreview.Options options = options();
        lines.add(line("screen.homelink_quarry.preview_state", Component.translatable(options.any()
                ? "screen.homelink_quarry.preview_on" : "screen.homelink_quarry.preview_off"), options.any() ? QuarryTheme.ACCENT : LABEL));
    }

    private Component areaValue() {
        return menu.hasArea() ? Component.literal(menu.width() + " × " + menu.length()) : Component.translatable("screen.homelink_quarry.no_area");
    }

    // ---- Rendering ------------------------------------------------------------------------------

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        QuarryTheme.window(graphics, leftPos, topPos, imageWidth, imageHeight, HEADER_HEIGHT);
        if (helpOpen) {
            help.render(graphics);
            return;
        }
        if (view == View.OUTPUT) {
            for (var slot : menu.slots) if (slot.isActive()) QuarryTheme.slot(graphics, leftPos + slot.x, topPos + slot.y);
            QuarryTheme.divider(graphics, leftPos + 10, topPos + QuarryScreenLayout.BOTTOM - 4, imageWidth - 20);
            return;
        }
        QuarryTheme.divider(graphics, leftPos + 10, topPos + 52, imageWidth - 20);
        int firstRow = view == View.STATUS ? QuarryScreenLayout.ROW_2 : QuarryScreenLayout.ROW_1;
        QuarryTheme.divider(graphics, leftPos + 10, topPos + firstRow - 4, imageWidth - 20);
        QuarryTheme.panel(graphics, leftPos + 10, topPos + PANEL_TOP, imageWidth - 20, lines().size() * LINE_HEIGHT + 7);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        QuarryStatus status = menu.status();
        String statusText = font.plainSubstrByWidth(Component.translatable(status.key()).getString(), 90);
        int statusX = imageWidth - 38 - font.width(statusText);
        QuarryTheme.statusLight(graphics, statusX - 12, 8, QuarryTheme.status(status));
        graphics.drawString(font, statusText, statusX, 8, LABEL, false);
        Optional<QuarryControllerBlockEntity> device = device();
        Component heading = device.map(QuarryControllerBlockEntity::displayName).orElse(title);
        boolean renamed = device.filter(named -> !named.customName().isEmpty()).isPresent();
        graphics.drawString(font, font.plainSubstrByWidth(heading.getString(), statusX - 30), 14, 8, renamed ? QuarryTheme.ACCENT : TEXT, false);
        if (helpOpen) {
            graphics.drawString(font, Component.translatable("screen.homelink_quarry.help.title"), 12, 30, QuarryTheme.ACCENT, false);
            return;
        }
        if (view == View.OUTPUT) {
            graphics.drawString(font, Component.translatable("screen.homelink_quarry.head"), QuarryMenu.HEAD_X + 22, QuarryMenu.HEAD_Y + 4, LABEL, false);
            graphics.drawString(font, Component.translatable("screen.homelink_quarry.energy_value", menu.energyPercent()),
                    QuarryMenu.ENERGY_X, QuarryMenu.ENERGY_Y + 4, menu.energyPercent() == 0 ? BAD : LABEL, false);
            String buffer = menu.bufferUsed() + " / " + QuarryControllerBlockEntity.BUFFER_SLOTS;
            graphics.drawString(font, Component.translatable("screen.homelink_quarry.buffer"), QuarryMenu.BUFFER_X, QuarryMenu.BUFFER_Y - 11, LABEL, false);
            graphics.drawString(font, buffer, QuarryMenu.BUFFER_X + 162 - font.width(buffer), QuarryMenu.BUFFER_Y - 11,
                    menu.bufferUsed() >= QuarryControllerBlockEntity.BUFFER_SLOTS ? BAD : TEXT, false);
            graphics.drawString(font, playerInventoryTitle, QuarryMenu.INVENTORY_X, QuarryMenu.INVENTORY_Y - 11, LABEL, false);
            return;
        }
        List<Line> lines = lines();
        int valueX = VALUE_X;
        for (Line line : lines) valueX = Math.max(valueX, 16 + font.width(line.label()) + 8);
        int y = LINES_TOP;
        for (Line line : lines) {
            graphics.drawString(font, line.label(), 16, y, LABEL, false);
            String value = font.plainSubstrByWidth(line.value().getString(), imageWidth - 16 - valueX);
            graphics.drawString(font, value, valueX, y, line.color(), false);
            if (line.bar() >= 0) {
                int barX = valueX + font.width(value) + 6;
                QuarryTheme.gauge(graphics, barX, y + 2, imageWidth - 16 - barX, line.bar(), line.color() == TEXT ? QuarryTheme.ACCENT : line.color());
            }
            y += LINE_HEIGHT;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (helpOpen) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                toggleHelp();
                return true;
            }
            if (help.keyPressed(keyCode)) {
                refreshHelp();
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (nameBox != null && nameBox.isFocused()) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                sendRename();
                return true;
            }
            if (keyCode != GLFW.GLFW_KEY_ESCAPE) return nameBox.keyPressed(keyCode, scanCode, modifiers) || nameBox.canConsumeInput();
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (helpOpen && help.mouseScrolled(mouseX, mouseY, deltaY)) {
            refreshHelp();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    // ---- Formatting -----------------------------------------------------------------------------

    private static Line line(String labelKey, Component value, int color) {
        return new Line(Component.translatable(labelKey), value, color, -1);
    }

    private static Component dash() {
        return Component.literal("—");
    }

    private static String roman(int maxSide) {
        return maxSide >= 32 ? "III" : maxSide >= 16 ? "II" : "I";
    }

    private static Locale locale() {
        String code = net.minecraft.client.Minecraft.getInstance().getLanguageManager().getSelected();
        return Locale.forLanguageTag(code.replace('_', '-'));
    }

    /** Percentage with one decimal, written the way the selected language writes numbers (64.3% / 64,3 %). */
    static Component percent(float fraction) {
        String number = String.format(locale(), "%.1f", Math.round(fraction * 1000) / 10.0F);
        return Component.translatable("screen.homelink_quarry.percent", number);
    }

    /** Grouped number in the selected language, with plain spaces the game font can draw. */
    static Component number(long value) {
        return Component.literal(String.format(locale(), "%,d", value).replace(' ', ' ').replace(' ', ' '));
    }

    static Component runtime(long seconds) {
        if (seconds <= 0) return Component.translatable("screen.homelink_quarry.empty");
        if (seconds < 60) return Component.translatable("screen.homelink_quarry.seconds", seconds);
        if (seconds < 3600) return Component.translatable("screen.homelink_quarry.minutes", seconds / 60);
        return Component.translatable("screen.homelink_quarry.hours", seconds / 3600, String.format(Locale.ROOT, "%02d", (seconds % 3600) / 60));
    }
}
