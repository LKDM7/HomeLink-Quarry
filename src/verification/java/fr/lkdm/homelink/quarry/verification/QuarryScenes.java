package fr.lkdm.homelink.quarry.verification;

import static fr.lkdm.homelink.quarry.verification.QuarrySmoke.check;
import static fr.lkdm.homelink.quarry.verification.QuarrySmoke.client;
import static fr.lkdm.homelink.quarry.verification.QuarrySmoke.log;
import static fr.lkdm.homelink.quarry.verification.QuarrySmoke.screenshot;
import static fr.lkdm.homelink.quarry.verification.QuarrySmoke.server;
import static fr.lkdm.homelink.quarry.verification.QuarrySmoke.waitTicks;

import fr.lkdm.homelink.quarry.blockentity.QuarryControllerBlockEntity;
import fr.lkdm.homelink.quarry.quarry.AreaCheck;
import fr.lkdm.homelink.quarry.registry.QuarryRegistries;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** The scripted in-game scenes, one block per development phase. */
final class QuarryScenes {
    /** Flat world: grass at y = -61, players stand at y = -60. */
    static final int GROUND = -60;
    static final BlockPos ROW = new BlockPos(0, GROUND, 6);

    private QuarryScenes() {
    }

    static void build(List<QuarrySmoke.Step> steps) {
        foundations(steps);
        areaSelection(steps);
        energy(steps);
        mining(steps);
        interfaceAndRestart(steps);
        preview(steps);
        outputPort(steps);
        homeCore(steps);
    }

    private static final java.util.UUID[] NETWORK = new java.util.UUID[1];

    /** HomeNetwork binding from the screen, then remote control through HomeCore's gateway. */
    private static void homeCore(List<QuarrySmoke.Step> steps) {
        steps.add(server(player -> {
            NETWORK[0] = fr.lkdm.homecore.api.DashboardAPI.networks(player.server).createNetwork("Maison", player.getUUID()).id();
            var entity = (QuarryControllerBlockEntity) player.level().getBlockEntity(PREVIEW_QUARRY);
            check(entity.device().isPresent(), "Quarry not registered with HomeCore");
            entity.resume();
            camera(player, 20.5, GROUND, 3.5, 0F, 20F);
            use(player, PREVIEW_QUARRY, false);
        }));
        steps.add(QuarrySmoke.until(client -> client.screen instanceof fr.lkdm.homelink.quarry.client.screen.QuarryScreen));
        steps.add(waitTicks(10));
        steps.add(click(fr.lkdm.homelink.quarry.client.screen.QuarryScreenLayout.NETWORK));
        steps.add(QuarrySmoke.serverUntil(player -> ((QuarryControllerBlockEntity) player.level().getBlockEntity(PREVIEW_QUARRY))
                .homeNetwork().filter(id -> id.equals(NETWORK[0])).isPresent()));
        steps.add(server(player -> {
            var entity = (QuarryControllerBlockEntity) player.level().getBlockEntity(PREVIEW_QUARRY);
            var networks = fr.lkdm.homecore.api.DashboardAPI.networks(player.server);
            check(networks.getNetwork(NETWORK[0]).orElseThrow().devices().contains(entity.deviceId()), "Device not in the network");
            var result = fr.lkdm.homecore.api.DashboardAPI.executeAction(player, NETWORK[0], entity.deviceId(),
                    fr.lkdm.homelink.quarry.homelink.QuarryIds.ACTION_PAUSE, fr.lkdm.homecore.api.action.Unit.INSTANCE);
            check(result.isSuccess() && entity.paused(), "Remote PAUSE through HomeCore: " + result.code());
        }));
        steps.add(QuarrySmoke.until(client -> ((fr.lkdm.homelink.quarry.menu.QuarryMenu) client.player.containerMenu).status()
                == fr.lkdm.homelink.quarry.quarry.QuarryStatus.PAUSED));
        steps.add(waitTicks(10));
        steps.add(screenshot("2b-gui-network-remote-pause"));
        steps.add(server(player -> {
            var entity = (QuarryControllerBlockEntity) player.level().getBlockEntity(PREVIEW_QUARRY);
            var result = fr.lkdm.homecore.api.DashboardAPI.executeAction(player, NETWORK[0], entity.deviceId(),
                    fr.lkdm.homelink.quarry.homelink.QuarryIds.ACTION_RESUME, fr.lkdm.homecore.api.action.Unit.INSTANCE);
            check(result.isSuccess() && !entity.paused(), "Remote RESUME through HomeCore: " + result.code());
        }));
        steps.add(client(client -> client.player.closeContainer()));
        steps.add(log("QUARRY_HOMECORE_CLIENT_OK"));
    }

    static final BlockPos PREVIEW_QUARRY = new BlockPos(20, GROUND, 6);
    static final BlockPos PREVIEW_A = new BlockPos(14, GROUND - 1, 10);
    static final BlockPos PREVIEW_B = new BlockPos(25, GROUND - 1, 19);
    static final BlockPos INVALID_QUARRY = new BlockPos(40, GROUND, 6);

    private static fr.lkdm.homelink.quarry.client.QuarryPreview.Options options(net.minecraft.client.Minecraft client, BlockPos pos) {
        return fr.lkdm.homelink.quarry.client.QuarryPreview.get(client.level.dimension(), pos);
    }

    /** The personal 3D preview, valid and invalid, layers, progress, target and distance. */
    private static void preview(List<QuarrySmoke.Step> steps) {
        steps.add(server(player -> {
            var level = player.serverLevel();
            level.setBlockAndUpdate(PREVIEW_QUARRY, QuarryRegistries.QUARRY_II.get().defaultBlockState()
                    .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
            var entity = (QuarryControllerBlockEntity) level.getBlockEntity(PREVIEW_QUARRY);
            check(entity.setCorners(PREVIEW_A, PREVIEW_B), "Corners");
            entity.headSlot().setStackInSlot(0, new ItemStack(QuarryRegistries.MINING_HEAD_III.get()));
            entity.energyPort().insert(Long.MAX_VALUE, false);
            level.setBlockAndUpdate(INVALID_QUARRY, QuarryRegistries.QUARRY_I.get().defaultBlockState()
                    .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
            var invalid = (QuarryControllerBlockEntity) level.getBlockEntity(INVALID_QUARRY);
            invalid.setCorners(new BlockPos(34, GROUND - 1, 10), new BlockPos(45, GROUND - 1, 17));
            player.getInventory().selected = 8;
            camera(player, 20.5, GROUND, 4.5, 180F, 20F);
            use(player, PREVIEW_QUARRY, false);
        }));
        steps.add(QuarrySmoke.until(client -> client.screen instanceof fr.lkdm.homelink.quarry.client.screen.QuarryScreen));
        steps.add(waitTicks(5));
        steps.add(click(fr.lkdm.homelink.quarry.client.screen.QuarryScreenLayout.AREA));
        steps.add(waitTicks(3));
        steps.add(click(fr.lkdm.homelink.quarry.client.screen.QuarryScreenLayout.PREVIEW));
        steps.add(waitTicks(3));
        steps.add(client(client -> {
            check(client.screen == null, "PREVIEW did not close the screen");
            check(options(client, PREVIEW_QUARRY).area, "PREVIEW did not show the area");
        }));
        steps.add(server(player -> camera(player, 19.5, GROUND + 14, 3.0, 0F, 52F)));
        steps.add(waitTicks(20));
        steps.add(client(client -> check(fr.lkdm.homelink.quarry.client.QuarryAreaRenderer.drawnLastFrame() == 1, "Preview not drawn")));
        steps.add(screenshot("2a-preview-valid"));
        steps.add(client(client -> options(client, PREVIEW_QUARRY).layers = true));
        steps.add(server(player -> {
            var entity = (QuarryControllerBlockEntity) player.level().getBlockEntity(PREVIEW_QUARRY);
            // A tall volume (above the flat ground) so several 8-block layer planes are visible.
            entity.setCorners(PREVIEW_A.above(34), PREVIEW_B);
            camera(player, 4.5, GROUND + 22, -6.0, -35F, 18F);
        }));
        steps.add(waitTicks(20));
        steps.add(client(client -> check(((QuarryControllerBlockEntity) client.level.getBlockEntity(PREVIEW_QUARRY)).area().orElseThrow()
                .startY() == PREVIEW_A.getY() + 34, "New corners not synced to the client")));
        steps.add(screenshot("2a-preview-layers"));
        steps.add(client(client -> {
            var options = options(client, PREVIEW_QUARRY);
            options.layers = false;
            options.progress = true;
        }));
        steps.add(server(player -> {
            var entity = (QuarryControllerBlockEntity) player.level().getBlockEntity(PREVIEW_QUARRY);
            check(entity.setCorners(PREVIEW_A, PREVIEW_B), "Corners reset");
            check(entity.start(), "Preview quarry start: " + entity.status());
            camera(player, 19.5, GROUND + 12, 5.0, 0F, 48F);
        }));
        steps.add(waitTicks(20 * 12));
        steps.add(client(client -> {
            var entity = (QuarryControllerBlockEntity) client.level.getBlockEntity(PREVIEW_QUARRY);
            check(entity.currentTarget() != null && entity.cursor() > 0, "Client did not receive the current target");
        }));
        steps.add(screenshot("2a-preview-progress"));
        // Invalid area: red / dark copper, START impossible.
        steps.add(client(client -> {
            options(client, PREVIEW_QUARRY).progress = false;
            options(client, INVALID_QUARRY).area = true;
        }));
        steps.add(server(player -> {
            var invalid = (QuarryControllerBlockEntity) player.level().getBlockEntity(INVALID_QUARRY);
            check(!invalid.checkArea().valid() && !invalid.start(), "Invalid area accepted");
            camera(player, 39.5, GROUND + 12, 5.0, 0F, 50F);
        }));
        steps.add(waitTicks(20));
        steps.add(client(client -> check(!((QuarryControllerBlockEntity) client.level.getBlockEntity(INVALID_QUARRY)).checkAreaClient(),
                "Client does not know the area is invalid")));
        steps.add(screenshot("2a-preview-invalid"));
        // Beyond quarryPreviewRenderDistance (64) nothing is drawn.
        steps.add(server(player -> camera(player, 20.5, GROUND + 5, 110.0, 180F, 10F)));
        steps.add(waitTicks(20));
        steps.add(client(client -> check(fr.lkdm.homelink.quarry.client.QuarryAreaRenderer.drawnLastFrame() == 0,
                "Preview drawn beyond the render distance")));
        steps.add(client(client -> {
            options(client, PREVIEW_QUARRY).area = false;
            options(client, INVALID_QUARRY).area = false;
        }));
        steps.add(log("QUARRY_PREVIEW_CLIENT_OK"));
    }

    /** ITEM_OUTPUT to a real Storage Deposit (or the dropper stand-in without HomeLink Storage). */
    private static void outputPort(List<QuarrySmoke.Step> steps) {
        BlockPos behind = PREVIEW_QUARRY.south();
        boolean storage = Boolean.getBoolean("quarry.withStorage");
        steps.add(server(player -> {
            var level = player.serverLevel();
            var entity = (QuarryControllerBlockEntity) level.getBlockEntity(PREVIEW_QUARRY);
            entity.pause();
            entity.buffer().setStackInSlot(20, new ItemStack(net.minecraft.world.item.Items.DIAMOND, 9));
            net.minecraft.world.level.block.state.BlockState input;
            if (storage) {
                var block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(
                        net.minecraft.resources.ResourceLocation.parse("homelink_storage:storage_deposit"));
                check(block != net.minecraft.world.level.block.Blocks.AIR, "HomeLink Storage Deposit missing");
                input = block.defaultBlockState();
                // The Deposit's screen faces away from the quarry; its back touches the ITEM_OUTPUT port.
                for (var property : input.getProperties())
                    if (property instanceof net.minecraft.world.level.block.state.properties.DirectionProperty direction
                            && direction.getPossibleValues().contains(Direction.SOUTH)) input = input.setValue(direction, Direction.SOUTH);
            } else {
                input = net.minecraft.world.level.block.Blocks.DROPPER.defaultBlockState();
            }
            level.setBlockAndUpdate(behind, input);
            camera(player, 22.6, GROUND + 1.6, 9.4, 140F, 35F);
        }));
        steps.add(waitTicks(4));
        steps.add(screenshot("2a-port-lighting"));
        steps.add(waitTicks(20));
        steps.add(server(player -> {
            var entity = (QuarryControllerBlockEntity) player.level().getBlockEntity(PREVIEW_QUARRY);
            check(entity.outputConnected(), "ITEM_INPUT not connected");
        }));
        steps.add(screenshot("2a-port-connected"));
        steps.add(QuarrySmoke.serverUntil(player ->
                ((QuarryControllerBlockEntity) player.level().getBlockEntity(PREVIEW_QUARRY)).bufferUsedSlots() == 0));
        steps.add(server(player -> {
            var handler = player.level().getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, behind, null);
            int diamonds = 0;
            for (int slot = 0; handler != null && slot < handler.getSlots(); slot++)
                if (handler.getStackInSlot(slot).is(net.minecraft.world.item.Items.DIAMOND)) diamonds += handler.getStackInSlot(slot).getCount();
            check(diamonds == 9, "Deposit received " + diamonds + " diamonds instead of 9");
            player.level().setBlockAndUpdate(behind, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            var entity = (QuarryControllerBlockEntity) player.level().getBlockEntity(PREVIEW_QUARRY);
            entity.buffer().setStackInSlot(0, new ItemStack(net.minecraft.world.item.Items.EMERALD, 3));
        }));
        steps.add(waitTicks(10));
        steps.add(server(player -> {
            var entity = (QuarryControllerBlockEntity) player.level().getBlockEntity(PREVIEW_QUARRY);
            check(!entity.outputConnected() && count(entity, net.minecraft.world.item.Items.EMERALD) == 3, "Disconnection");
        }));
        steps.add(screenshot("2a-port-disconnected"));
        steps.add(log(storage ? "QUARRY_PORT_CLIENT_OK storage=true" : "QUARRY_PORT_CLIENT_OK storage=false"));
    }

    private static final long[] BEFORE_RESTART = new long[4];

    /** Phase 6: the real screen, driven with mouse clicks, then a save/quit/reload of the world. */
    private static void interfaceAndRestart(List<QuarrySmoke.Step> steps) {
        steps.add(server(player -> {
            player.getInventory().selected = 8;
            player.getInventory().setItem(8, ItemStack.EMPTY);
            camera(player, -5.5, GROUND, 19.5, 0F, 20F);
            use(player, MINING_CONTROLLER, false);
        }));
        steps.add(QuarrySmoke.until(client -> client.screen instanceof fr.lkdm.homelink.quarry.client.screen.QuarryScreen));
        steps.add(waitTicks(15));
        steps.add(client(client -> {
            var menu = (fr.lkdm.homelink.quarry.menu.QuarryMenu) client.player.containerMenu;
            check(menu.width() == 6 && menu.length() == 6, "GUI area " + menu.width() + "x" + menu.length());
            check(menu.headLevel() == 3 && menu.status() == fr.lkdm.homelink.quarry.quarry.QuarryStatus.MINING, "GUI head/status " + menu.status());
            check(menu.blocksMined() > 0 && menu.progress() > 0 && menu.bufferUsed() > 0, "GUI counters");
            check(menu.energyPercent() > 0 && menu.runtimeSeconds() > 0 && menu.estimatedBlocks() > 0, "GUI energy");
        }));
        steps.add(screenshot("phase6-gui-mining"));
        steps.add(click(fr.lkdm.homelink.quarry.client.screen.QuarryScreenLayout.PRIMARY));
        steps.add(QuarrySmoke.until(client -> ((fr.lkdm.homelink.quarry.menu.QuarryMenu) client.player.containerMenu).status()
                == fr.lkdm.homelink.quarry.quarry.QuarryStatus.PAUSED));
        steps.add(waitTicks(5));
        steps.add(screenshot("phase6-gui-paused"));
        steps.add(click(fr.lkdm.homelink.quarry.client.screen.QuarryScreenLayout.PRIMARY));
        steps.add(QuarrySmoke.until(client -> ((fr.lkdm.homelink.quarry.menu.QuarryMenu) client.player.containerMenu).status()
                == fr.lkdm.homelink.quarry.quarry.QuarryStatus.MINING));
        // STOP keeps everything, then Stop Y can be edited from the screen.
        steps.add(click(fr.lkdm.homelink.quarry.client.screen.QuarryScreenLayout.STOP));
        steps.add(QuarrySmoke.until(client -> ((fr.lkdm.homelink.quarry.menu.QuarryMenu) client.player.containerMenu).status()
                == fr.lkdm.homelink.quarry.quarry.QuarryStatus.READY));
        steps.add(server(player -> {
            var entity = (QuarryControllerBlockEntity) player.level().getBlockEntity(MINING_CONTROLLER);
            check(entity.blocksMined() > 0 && entity.area().isPresent() && entity.miningHead().isPresent(), "STOP lost data");
        }));
        // AREA view: depth and preview options.
        steps.add(waitTicks(3));
        steps.add(click(fr.lkdm.homelink.quarry.client.screen.QuarryScreenLayout.AREA));
        steps.add(waitTicks(3));
        steps.add(click(fr.lkdm.homelink.quarry.client.screen.QuarryScreenLayout.STOP_Y_UP));
        steps.add(waitTicks(5));
        steps.add(server(player -> {
            var entity = (QuarryControllerBlockEntity) player.level().getBlockEntity(MINING_CONTROLLER);
            check(entity.area().orElseThrow().stopY() == player.level().getMinBuildHeight() + 1, "Stop Y button");
        }));
        steps.add(click(fr.lkdm.homelink.quarry.client.screen.QuarryScreenLayout.SHOW_LAYERS));
        steps.add(waitTicks(5));
        steps.add(client(client -> check(fr.lkdm.homelink.quarry.client.QuarryPreview.get(client.level.dimension(), MINING_CONTROLLER).layers,
                "SHOW LAYERS did not turn on")));
        steps.add(screenshot("phase6-gui-area-view"));
        steps.add(click(fr.lkdm.homelink.quarry.client.screen.QuarryScreenLayout.SHOW_LAYERS));
        steps.add(click(fr.lkdm.homelink.quarry.client.screen.QuarryScreenLayout.BACK));
        // OUTPUT view: head, energy, buffer and inventory slots.
        steps.add(waitTicks(3));
        steps.add(click(fr.lkdm.homelink.quarry.client.screen.QuarryScreenLayout.OUTPUT));
        steps.add(waitTicks(5));
        steps.add(client(client -> check(client.player.containerMenu.slots.get(0).isActive(), "Slots hidden in the output view")));
        steps.add(screenshot("phase6-gui-output-view"));
        steps.add(click(fr.lkdm.homelink.quarry.client.screen.QuarryScreenLayout.BACK));
        steps.add(waitTicks(3));
        steps.add(client(client -> check(!client.player.containerMenu.slots.get(0).isActive(), "Slots visible in the status view")));
        // Help page, then rename through the real packet.
        steps.add(click(new int[]{fr.lkdm.homelink.quarry.client.screen.QuarryScreenLayout.WIDTH - 30, 3, 20, 18}));
        steps.add(waitTicks(5));
        steps.add(screenshot("phase6-gui-help"));
        steps.add(client(client -> client.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE, 0, 0)));
        steps.add(client(client -> net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                new fr.lkdm.homelink.quarry.network.QuarryPayloads.Rename(MINING_CONTROLLER, "Carrière Nord"))));
        steps.add(waitTicks(10));
        steps.add(server(player -> check(((QuarryControllerBlockEntity) player.level().getBlockEntity(MINING_CONTROLLER))
                .customName().equals("Carrière Nord"), "Rename refused")));
        steps.add(client(client -> check(((QuarryControllerBlockEntity) client.level.getBlockEntity(MINING_CONTROLLER))
                .displayName().getString().equals("Carrière Nord"), "Name not synced to the client")));
        steps.add(click(fr.lkdm.homelink.quarry.client.screen.QuarryScreenLayout.PRIMARY));
        steps.add(QuarrySmoke.until(client -> ((fr.lkdm.homelink.quarry.menu.QuarryMenu) client.player.containerMenu).status()
                == fr.lkdm.homelink.quarry.quarry.QuarryStatus.MINING));
        steps.add(waitTicks(20 * 7));
        steps.add(screenshot("phase6-gui-restarted-job"));
        steps.add(client(client -> client.player.closeContainer()));
        steps.add(server(player -> {
            var entity = (QuarryControllerBlockEntity) player.level().getBlockEntity(MINING_CONTROLLER);
            check(entity.pause(), "Pause before restart");
            BEFORE_RESTART[0] = entity.cursor();
            BEFORE_RESTART[1] = entity.blocksMined();
            BEFORE_RESTART[2] = entity.storedEnergy();
            BEFORE_RESTART[3] = count(entity, net.minecraft.world.item.Items.DIAMOND);
            check(BEFORE_RESTART[1] > 0, "Nothing mined before restart");
        }));
        steps.add(QuarrySmoke.restart());
        steps.add(server(player -> {
            var entity = (QuarryControllerBlockEntity) player.level().getBlockEntity(MINING_CONTROLLER);
            check(entity != null, "Controller missing after restart");
            check(entity.paused() && entity.status() == fr.lkdm.homelink.quarry.quarry.QuarryStatus.PAUSED, "Pause lost: " + entity.status());
            check(entity.cursor() == BEFORE_RESTART[0] && entity.blocksMined() == BEFORE_RESTART[1], "Progress lost after restart");
            check(entity.storedEnergy() == BEFORE_RESTART[2] && count(entity, net.minecraft.world.item.Items.DIAMOND) == BEFORE_RESTART[3],
                    "Energy or buffer changed after restart");
            check(entity.miningHead().isPresent() && entity.area().orElseThrow().width() == 6, "Head or area lost after restart");
            check(entity.resume(), "Resume after restart");
        }));
        steps.add(waitTicks(20 * 4));
        steps.add(server(player -> {
            var entity = (QuarryControllerBlockEntity) player.level().getBlockEntity(MINING_CONTROLLER);
            check(entity.blocksMined() > BEFORE_RESTART[1], "Quarry did not continue after restart");
        }));
        steps.add(log("QUARRY_INTERFACE_RESTART_CLIENT_OK"));
    }

    /** Clicks a screen control with the real mouse handler, at its layout position. */
    static QuarrySmoke.Step click(int[] control) {
        return client -> {
            if (!(client.screen instanceof fr.lkdm.homelink.quarry.client.screen.QuarryScreen screen)) return false;
            double x = screen.getGuiLeft() + control[0] + control[2] / 2.0;
            double y = screen.getGuiTop() + control[1] + control[3] / 2.0;
            check(screen.mouseClicked(x, y, 0), "Click missed at " + control[0] + "," + control[1]);
            screen.mouseReleased(x, y, 0);
            return true;
        };
    }

    static final BlockPos MINING_CONTROLLER = new BlockPos(-6, GROUND, 22);
    static final BlockPos MINING_A = new BlockPos(-3, GROUND - 1, 24);
    static final BlockPos MINING_B = new BlockPos(2, GROUND - 1, 29);
    static final BlockPos CHEST = new BlockPos(-2, GROUND - 1, 24);

    /** Phase 5: a Quarry III with Head III works on a 6x6 area with ores and a chest. */
    private static void mining(List<QuarrySmoke.Step> steps) {
        steps.add(server(player -> {
            var level = player.serverLevel();
            level.setBlockAndUpdate(MINING_CONTROLLER, QuarryRegistries.QUARRY_III.get().defaultBlockState()
                    .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
            level.setBlockAndUpdate(MINING_A.offset(0, 0, 0), net.minecraft.world.level.block.Blocks.IRON_ORE.defaultBlockState());
            level.setBlockAndUpdate(MINING_A.offset(3, 0, 1), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(CHEST, net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
            var chest = (net.minecraft.world.level.block.entity.ChestBlockEntity) level.getBlockEntity(CHEST);
            chest.setItem(0, new ItemStack(net.minecraft.world.item.Items.DIAMOND, 12));
            chest.setItem(5, new ItemStack(net.minecraft.world.item.Items.TORCH, 32));
            var entity = (QuarryControllerBlockEntity) level.getBlockEntity(MINING_CONTROLLER);
            check(entity.setCorners(MINING_A, MINING_B), "Corners refused");
            entity.headSlot().setStackInSlot(0, new ItemStack(QuarryRegistries.MINING_HEAD_III.get()));
            entity.energyPort().insert(Long.MAX_VALUE, false);
            check(entity.start(), "Quarry III refused to start: " + entity.status());
            camera(player, -0.5, GROUND + 7, 19.0, 0F, 50F);
        }));
        steps.add(waitTicks(45));
        steps.add(screenshot("phase5-drilling-first-block"));
        // Close-ups while the head drills (lighting must not depend on the drilling bob).
        steps.add(server(player -> camera(player, -5.0, GROUND - 0.2, 21.0, -35F, 22F)));
        steps.add(waitTicks(3));
        steps.add(screenshot("phase5-head-closeup-a"));
        steps.add(waitTicks(4));
        steps.add(screenshot("phase5-head-closeup-b"));
        // At night the working head lights the ground around it like a torch.
        steps.add(server(player -> player.serverLevel().setDayTime(18000)));
        steps.add(waitTicks(10));
        steps.add(client(client -> {
            var entity = (QuarryControllerBlockEntity) client.level.getBlockEntity(MINING_CONTROLLER);
            BlockPos target = entity.currentTarget();
            check(target != null && client.level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, target.above()) == 14,
                    "The working head does not light its cell (client)");
        }));
        steps.add(screenshot("phase5-head-night-light"));
        steps.add(server(player -> player.serverLevel().setDayTime(6000)));
        steps.add(server(player -> camera(player, -0.5, GROUND + 7, 19.0, 0F, 50F)));
        steps.add(waitTicks(20 * 20));
        steps.add(server(player -> {
            var entity = (QuarryControllerBlockEntity) player.level().getBlockEntity(MINING_CONTROLLER);
            check(entity.status() == fr.lkdm.homelink.quarry.quarry.QuarryStatus.MINING, "Not mining: " + entity.status());
            check(entity.blocksMined() >= 6, "Too few blocks mined in 22 s: " + entity.blocksMined());
            check(count(entity, net.minecraft.world.item.Items.RAW_IRON) == 1, "Iron ore drop missing");
            check(count(entity, net.minecraft.world.item.Items.DIAMOND) == 12 && count(entity, net.minecraft.world.item.Items.TORCH) == 32
                    && count(entity, net.minecraft.world.item.Items.CHEST) == 1, "Chest or contents missing");
            var ground = player.level().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    new net.minecraft.world.phys.AABB(MINING_A).inflate(12));
            check(ground.isEmpty(), "Items lying on the ground: " + ground.size());
            check(entity.storedEnergy() < fr.lkdm.homelink.quarry.quarry.QuarryEnergy.capacity(), "No energy used");
            LOG_MINED[0] = entity.blocksMined();
        }));
        steps.add(screenshot("phase5-after-20s"));
        steps.add(server(player -> {
            var entity = (QuarryControllerBlockEntity) player.level().getBlockEntity(MINING_CONTROLLER);
            check(entity.pause(), "Pause refused");
            LOG_MINED[0] = entity.blocksMined();
        }));
        steps.add(waitTicks(100));
        steps.add(server(player -> {
            var entity = (QuarryControllerBlockEntity) player.level().getBlockEntity(MINING_CONTROLLER);
            check(entity.blocksMined() == LOG_MINED[0], "Paused quarry kept mining");
            check(entity.resume(), "Resume refused");
        }));
        steps.add(log("QUARRY_MINING_CLIENT_OK"));
    }

    private static final long[] LOG_MINED = new long[1];

    static int count(QuarryControllerBlockEntity entity, net.minecraft.world.item.Item item) {
        int total = 0;
        for (int slot = 0; slot < entity.buffer().getSlots(); slot++)
            if (entity.buffer().getStackInSlot(slot).is(item)) total += entity.buffer().getStackInSlot(slot).getCount();
        return total;
    }

    /** Phase 4: the quarry takes HE through the HomeCore energy capability; a hopper cannot feed it fuel anymore. */
    private static void energy(List<QuarrySmoke.Step> steps) {
        BlockPos quarry = new BlockPos(10, GROUND, 6);
        steps.add(server(player -> {
            var level = player.serverLevel();
            level.setBlockAndUpdate(quarry, QuarryRegistries.QUARRY_II.get().defaultBlockState()
                    .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
            level.setBlockAndUpdate(quarry.above(), net.minecraft.world.level.block.Blocks.HOPPER.defaultBlockState());
            var hopper = (net.minecraft.world.level.block.entity.HopperBlockEntity) level.getBlockEntity(quarry.above());
            hopper.setItem(0, new ItemStack(net.minecraft.world.item.Items.COAL, 5));
            hopper.setItem(1, new ItemStack(net.minecraft.world.item.Items.DIRT, 3));
            camera(player, 10.5, GROUND, 2.5, 0F, 20F);
        }));
        steps.add(waitTicks(60));
        steps.add(server(player -> {
            var entity = (QuarryControllerBlockEntity) player.level().getBlockEntity(quarry);
            var hopper = (net.minecraft.world.level.block.entity.HopperBlockEntity) player.level().getBlockEntity(quarry.above());
            check(hopper.countItem(net.minecraft.world.item.Items.COAL) == 5, "The quarry accepted coal");
            check(hopper.countItem(net.minecraft.world.item.Items.DIRT) == 3, "Dirt went into the quarry");
            var port = player.level().getCapability(fr.lkdm.homecore.api.energy.EnergyApi.BLOCK, quarry, Direction.EAST);
            check(port != null && port.insert(2_500, false) == 2_500, "No HE input on the quarry");
            check(entity.energyPercent() == 50 && entity.estimatedBlocks() == 0, "Energy without a head: " + entity.energyPercent());
        }));
        steps.add(screenshot("phase4-energy-input"));
        steps.add(log("QUARRY_ENERGY_CLIENT_OK"));
    }

    static List<Block> quarries() {
        return List.of(QuarryRegistries.QUARRY_I.get(), QuarryRegistries.QUARRY_II.get(), QuarryRegistries.QUARRY_III.get());
    }

    /** Phases 1 and 2: every controller model and orientation, head and marker items render in game. */
    private static void foundations(List<QuarrySmoke.Step> steps) {
        steps.add(server(player -> {
            var level = player.serverLevel();
            List<Block> blocks = quarries();
            for (int i = 0; i < 3; i++) {
                level.setBlockAndUpdate(ROW.offset(i * 2, 0, 0), blocks.get(i).defaultBlockState()
                        .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
            }
            Direction[] facings = {Direction.EAST, Direction.SOUTH, Direction.WEST};
            for (int i = 0; i < 3; i++) {
                level.setBlockAndUpdate(ROW.offset(i * 2, 0, 3), blocks.get(i).defaultBlockState()
                        .setValue(HorizontalDirectionalBlock.FACING, facings[i]));
            }
            var inventory = player.getInventory();
            inventory.clearContent();
            inventory.setItem(0, new ItemStack(QuarryRegistries.QUARRY_I_ITEM.get()));
            inventory.setItem(1, new ItemStack(QuarryRegistries.QUARRY_II_ITEM.get()));
            inventory.setItem(2, new ItemStack(QuarryRegistries.QUARRY_III_ITEM.get()));
            inventory.setItem(3, new ItemStack(QuarryRegistries.MINING_HEAD_I.get()));
            inventory.setItem(4, new ItemStack(QuarryRegistries.MINING_HEAD_II.get()));
            inventory.setItem(5, new ItemStack(QuarryRegistries.MINING_HEAD_III.get()));
            inventory.setItem(6, new ItemStack(QuarryRegistries.QUARRY_MARKER.get()));
            inventory.selected = 6;
            camera(player, 2.5, GROUND, 2.0, 0F, 25F);
        }));
        steps.add(client(QuarryScenes::checkModels));
        steps.add(waitTicks(40));
        steps.add(screenshot("phase1-controllers"));
        steps.add(server(player -> camera(player, 2.5, GROUND + 1, 13.5, 180F, 30F)));
        steps.add(waitTicks(20));
        steps.add(screenshot("phase1-orientations"));
        steps.add(log("QUARRY_FOUNDATION_CLIENT_OK"));
    }

    /** Phase 3: the marker is used like a player would, then applied to a controller. */
    private static void areaSelection(List<QuarrySmoke.Step> steps) {
        BlockPos controller = ROW;
        BlockPos cornerA = new BlockPos(-4, GROUND - 1, 12);
        BlockPos cornerB = new BlockPos(3, GROUND - 1, 19);
        steps.add(server(player -> {
            camera(player, 0.5, GROUND, 8.5, 180F, 20F);
            use(player, cornerA, false);
            use(player, cornerB, true);
            use(player, controller, false);
            QuarryControllerBlockEntity entity = (QuarryControllerBlockEntity) player.level().getBlockEntity(controller);
            check(entity.checkArea() == AreaCheck.VALID && entity.area().orElseThrow().width() == 8, "Marker 8x8 not applied");
        }));
        steps.add(waitTicks(10));
        steps.add(screenshot("phase3-area-applied"));
        steps.add(server(player -> {
            use(player, new BlockPos(3, GROUND - 1, 23), true);
            use(player, controller, false);
            QuarryControllerBlockEntity entity = (QuarryControllerBlockEntity) player.level().getBlockEntity(controller);
            check(entity.checkArea() == AreaCheck.TOO_LARGE, "12x8 accepted by Quarry I");
        }));
        steps.add(waitTicks(10));
        steps.add(screenshot("phase3-area-too-large"));
        steps.add(log("QUARRY_AREA_CLIENT_OK"));
    }

    /** Places the (flying) player so screenshots frame the scene. */
    static void camera(ServerPlayer player, double x, double y, double z, float yaw, float pitch) {
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        player.teleportTo(player.serverLevel(), x, y, z, yaw, pitch);
    }

    /** Right-clicks a block with the selected item, through the real server interaction path. */
    static void use(ServerPlayer player, BlockPos pos, boolean sneaking) {
        player.setShiftKeyDown(sneaking);
        ItemStack stack = player.getMainHandItem();
        player.gameMode.useItemOn(player, player.serverLevel(), stack, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        player.setShiftKeyDown(false);
    }

    private static void checkModels(Minecraft client) {
        var missing = client.getModelManager().getMissingModel();
        for (Block block : quarries()) {
            for (var state : block.getStateDefinition().getPossibleStates())
                check(client.getBlockRenderer().getBlockModel(state) != missing, "Missing block model " + state);
        }
        for (var item : QuarryRegistries.tabItems()) {
            check(client.getItemRenderer().getModel(item.get().getDefaultInstance(), client.level, client.player, 0) != missing,
                    "Missing item model " + item.getId());
        }
    }
}
