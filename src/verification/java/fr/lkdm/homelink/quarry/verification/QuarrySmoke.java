package fr.lkdm.homelink.quarry.verification;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.slf4j.Logger;

/**
 * In-game verification on the real client: creates a disposable flat creative world, plays a scripted
 * sequence of steps against the integrated server, takes screenshots and exits.
 * Enabled only by the {@code quarry.smoke} system property of the {@code runSmoke} task.
 */
@EventBusSubscriber(modid = QuarryValidation.MOD_ID, value = Dist.CLIENT)
public final class QuarrySmoke {
    static final Logger LOG = LogUtils.getLogger();
    private static final List<Step> STEPS = new ArrayList<>();
    private static int index = -1;
    private static long deadline;
    private static int waited;
    private static boolean serverPending;
    private static volatile boolean serverDone;
    private static volatile Throwable serverFailure;
    private static String worldId;
    private static int restartPhase;

    /** One scripted step; returns true once complete. */
    @FunctionalInterface
    interface Step {
        boolean run(Minecraft client);
    }

    private QuarrySmoke() {
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("quarry.smoke") || index == Integer.MAX_VALUE) return;
        Minecraft client = Minecraft.getInstance();
        try {
            if (index == -1) {
                if (client.screen instanceof AccessibilityOnboardingScreen screen) { screen.onClose(); return; }
                if (!(client.screen instanceof TitleScreen)) return;
                QuarryScenes.build(STEPS);
                index = 0;
                deadline = System.nanoTime() + 600_000_000_000L;
                GameRules rules = new GameRules();
                rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
                rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
                rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
                worldId = "quarry-validation-" + System.currentTimeMillis();
                client.createWorldOpenFlows().createFreshLevel(worldId,
                        new LevelSettings("Quarry Validation", GameType.CREATIVE, false, Difficulty.PEACEFUL,
                                true, rules, WorldDataConfiguration.DEFAULT),
                        new WorldOptions(731L, false, false),
                        access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT)
                                .value().createWorldDimensions(), new TitleScreen());
                return;
            }
            check(System.nanoTime() < deadline, "Timed out at step " + index);
            if (serverFailure != null) throw new IllegalStateException("Server step failed", serverFailure);
            if (restartPhase > 0) {
                restartTick(client);
                return;
            }
            if (client.player == null || client.getSingleplayerServer() == null || client.level == null) return;
            if (index >= STEPS.size()) {
                LOG.info("QUARRY_SMOKE_OK steps={}", STEPS.size());
                index = Integer.MAX_VALUE;
                client.stop();
                return;
            }
            if (STEPS.get(index).run(client)) {
                index++;
                waited = 0;
            }
        } catch (Throwable failure) {
            LOG.error("QUARRY_SMOKE_FAILED at step {}", index, failure);
            index = Integer.MAX_VALUE;
            client.stop();
        }
    }

    // ---- Step builders --------------------------------------------------------------------------

    /** Runs an action on the server thread with the server-side player, and waits for it. */
    static Step server(Consumer<ServerPlayer> action) {
        return client -> {
            if (!serverPending) {
                serverPending = true;
                serverDone = false;
                var server = client.getSingleplayerServer();
                var uuid = client.player.getUUID();
                server.execute(() -> {
                    try {
                        action.accept(server.getPlayerList().getPlayer(uuid));
                    } catch (Throwable failure) {
                        serverFailure = failure;
                    }
                    serverDone = true;
                });
                return false;
            }
            if (!serverDone) return false;
            serverPending = false;
            return true;
        };
    }

    /** Polls a condition on the server thread each tick until it holds. */
    static Step serverUntil(Predicate<ServerPlayer> condition) {
        boolean[] result = new boolean[1];
        Step poll = server(player -> result[0] = condition.test(player));
        return client -> poll.run(client) && result[0];
    }

    /** Runs a client-side action immediately. */
    static Step client(Consumer<Minecraft> action) {
        return client -> {
            action.accept(client);
            return true;
        };
    }

    /** Waits until a client condition holds, within the global deadline. */
    static Step until(Predicate<Minecraft> condition) {
        return condition::test;
    }

    static Step waitTicks(int ticks) {
        return client -> ++waited >= ticks;
    }

    static Step screenshot(String name) {
        boolean[] prepared = new boolean[1];
        return client -> {
            if (client.getOverlay() != null) return false;
            if (client.screen instanceof fr.lkdm.homelink.quarry.client.screen.QuarryScreen && !prepared[0]) {
                verifyControls(client);
                prepared[0] = true;
                return false;
            }
            Screenshot.grab(client.gameDirectory, "quarry-" + name + ".png", client.getMainRenderTarget(),
                    message -> LOG.info("QUARRY_SCREENSHOT {} {}", name, message.getString()));
            return true;
        };
    }

    private static void verifyControls(Minecraft client) {
        var screen = client.screen;
        for (var child : screen.children()) {
            if (!(child instanceof AbstractWidget widget) || !widget.visible) continue;
            check(widget.getX() >= 0 && widget.getY() >= 0
                            && widget.getX() + widget.getWidth() <= screen.width
                            && widget.getY() + widget.getHeight() <= screen.height,
                    "Control outside viewport: " + widget.getMessage().getString());
        }
        screen.setFocused(null);
        var expected = screen.children().stream().filter(AbstractWidget.class::isInstance)
                .map(AbstractWidget.class::cast).filter(widget -> widget.active && widget.visible).toList();
        check(!expected.isEmpty(), "Quarry screen has no active controls");
        var visited = new java.util.HashSet<AbstractWidget>();
        for (int i = 0; i < expected.size(); i++) {
            screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_TAB, 0, 0);
            check(screen.getFocused() instanceof AbstractWidget widget && widget.active && widget.visible
                            && widget.isFocused(), "Tab did not focus an enabled visible control");
            check(visited.add((AbstractWidget) screen.getFocused()), "Tab cycle repeated a control before visiting all controls");
        }
        check(visited.containsAll(expected), "Tab cycle skipped an active control");
        LOG.info("QUARRY_UI_CONTROLS_OK viewport={}x{}", screen.width, screen.height);
    }

    /** Resizes the verification client only; the launch task runs on an isolated desktop. */
    static void smallViewport(List<Step> steps) {
        int[] previous = new int[3];
        steps.add(client(client -> {
            previous[0] = client.getWindow().getWidth();
            previous[1] = client.getWindow().getHeight();
            previous[2] = client.options.guiScale().get();
            client.options.guiScale().set(2);
            org.lwjgl.glfw.GLFW.glfwSetWindowSize(client.getWindow().getWindow(), 640, 480);
            client.resizeDisplay();
        }));
        steps.add(waitTicks(5));
        steps.add(screenshot("phase6-gui-help-small"));
        steps.add(client(client -> {
            client.options.guiScale().set(previous[2]);
            org.lwjgl.glfw.GLFW.glfwSetWindowSize(client.getWindow().getWindow(), previous[0], previous[1]);
            client.resizeDisplay();
        }));
        steps.add(waitTicks(5));
    }

    static Step log(String marker) {
        return client -> {
            LOG.info(marker);
            return true;
        };
    }

    /** Saves and quits to the title screen, then reopens the same world, like a server restart. */
    static Step restart() {
        return client -> {
            if (restartPhase == 0) {
                restartPhase = 1;
                return false;
            }
            if (restartPhase == -1) {
                restartPhase = 0;
                return true;
            }
            return false;
        };
    }

    private static void restartTick(Minecraft client) {
        switch (restartPhase) {
            case 1 -> {
                LOG.info("QUARRY_RESTART saving and leaving the world");
                if (client.level != null) client.level.disconnect();
                client.disconnect(new net.minecraft.client.gui.screens.GenericMessageScreen(net.minecraft.network.chat.Component.literal("Saving")));
                client.setScreen(new TitleScreen());
                restartPhase = 2;
            }
            case 2 -> {
                if (client.level != null || client.getSingleplayerServer() != null || !(client.screen instanceof TitleScreen)) return;
                LOG.info("QUARRY_RESTART reopening {}", worldId);
                client.createWorldOpenFlows().openWorld(worldId, () -> client.setScreen(new TitleScreen()));
                restartPhase = 3;
            }
            case 3 -> {
                if (client.player == null || client.getSingleplayerServer() == null || client.level == null) return;
                if (++waited < 40) return;
                waited = 0;
                restartPhase = -1;
                STEPS.get(index).run(client);
                index++;
            }
            default -> { }
        }
    }

    static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
