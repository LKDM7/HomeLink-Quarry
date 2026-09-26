package fr.lkdm.homelink.quarry.verification;

import static fr.lkdm.homelink.quarry.verification.FoundationGameTests.check;

import com.mojang.authlib.GameProfile;
import fr.lkdm.homecore.api.DashboardAPI;
import fr.lkdm.homecore.api.action.ActionResult;
import fr.lkdm.homecore.api.device.DashboardDevice;
import fr.lkdm.homecore.api.device.DeviceStatus;
import fr.lkdm.homecore.api.event.DeviceEvent;
import fr.lkdm.homecore.api.metric.DeviceMetric;
import fr.lkdm.homecore.api.metric.Duration;
import fr.lkdm.homecore.api.metric.Percentage;
import fr.lkdm.homelink.quarry.blockentity.QuarryControllerBlockEntity;
import fr.lkdm.homelink.quarry.homelink.QuarryIds;
import fr.lkdm.homelink.quarry.quarry.QuarryStatus;
import fr.lkdm.homelink.quarry.registry.QuarryRegistries;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Prompt 2B: the Quarry Controller as a real HomeCore device (identity, metrics, actions, events). */
@GameTestHolder(QuarryValidation.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HomeCoreGameTests {
    private static final BlockPos QUARRY = new BlockPos(1, 1, 1);

    private static QuarryControllerBlockEntity quarry(GameTestHelper helper) {
        helper.setBlock(QUARRY, QuarryRegistries.QUARRY_II.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
        QuarryControllerBlockEntity entity = helper.getBlockEntity(QUARRY);
        BlockPos a = helper.absolutePos(new BlockPos(3, 1, 0));
        entity.setCorners(a, a.offset(3, 0, 3));
        entity.setStopY(a.getY());
        return entity;
    }

    private static DashboardDevice device(GameTestHelper helper, QuarryControllerBlockEntity entity) {
        return DashboardAPI.devices(helper.getLevel().getServer()).get(entity.deviceId()).orElse(null);
    }

    @SuppressWarnings("unchecked")
    private static <T> T metric(DashboardDevice device, ResourceLocation id) {
        return (T) device.metrics().stream().filter(metric -> metric.id().equals(id)).findFirst().map(DeviceMetric::value).orElseThrow();
    }

    private static FakePlayer player(GameTestHelper helper, String name) {
        return FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.nameUUIDFromBytes(name.getBytes()), name));
    }

    @GameTest(template = "empty", batch = "homecore_a", timeoutTicks = 60)
    public static void registeredWithStableIdentityAndSchema(GameTestHelper helper) {
        QuarryControllerBlockEntity entity = quarry(helper);
        helper.runAtTickTime(5, () -> {
            DashboardDevice device = device(helper, entity);
            check(helper, device != null, "Quarry not registered with HomeCore");
            check(helper, device.deviceType().equals(QuarryIds.DEVICE_TYPE), "Device type");
            check(helper, device.position().orElseThrow().equals(entity.getBlockPos()) && device.dimension().isPresent(), "Location");
            var schema = device.schema();
            for (ResourceLocation id : List.of(QuarryIds.STATUS, QuarryIds.QUARRY_LEVEL, QuarryIds.AREA_WIDTH, QuarryIds.AREA_LENGTH,
                    QuarryIds.START_Y, QuarryIds.STOP_Y, QuarryIds.CURRENT_LAYER, QuarryIds.PROGRESS, QuarryIds.BLOCKS_MINED,
                    QuarryIds.BLOCKS_REMAINING, QuarryIds.MINING_HEAD_LEVEL, QuarryIds.MINING_SPEED, QuarryIds.FUEL_PERCENTAGE,
                    QuarryIds.RUNTIME_REMAINING, QuarryIds.ESTIMATED_BLOCKS, QuarryIds.OUTPUT_USAGE, QuarryIds.OUTPUT_ITEM_COUNT,
                    QuarryIds.STORAGE_CONNECTED)) {
                check(helper, schema.metrics().stream().anyMatch(metric -> metric.id().equals(id)), "Missing metric " + id);
            }
            check(helper, schema.actions().size() == 4 && schema.events().containsAll(QuarryIds.EVENTS), "Actions or events missing");
            // The identity survives a save/load.
            var registries = helper.getLevel().registryAccess();
            QuarryControllerBlockEntity copy = new QuarryControllerBlockEntity(entity.getBlockPos(), entity.getBlockState());
            copy.loadWithComponents(entity.saveWithoutMetadata(registries), registries);
            check(helper, copy.deviceId().equals(entity.deviceId()), "UUID changed on reload");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "homecore_b", timeoutTicks = 60)
    public static void metricsAndStatus(GameTestHelper helper) {
        QuarryControllerBlockEntity entity = quarry(helper);
        entity.headSlot().setStackInSlot(0, new ItemStack(QuarryRegistries.MINING_HEAD_III.get()));
        entity.fuelSlot().setStackInSlot(0, new ItemStack(Items.COAL, 4));
        entity.buffer().setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 32));
        helper.runAtTickTime(25, () -> {
            DashboardDevice device = device(helper, entity);
            check(helper, device != null, "Not registered");
            check(helper, metric(device, QuarryIds.STATUS) == QuarryStatus.READY, "Status metric");
            check(helper, (int) metric(device, QuarryIds.QUARRY_LEVEL) == 2, "Level metric");
            check(helper, (int) metric(device, QuarryIds.AREA_WIDTH) == 4 && (int) metric(device, QuarryIds.AREA_LENGTH) == 4, "Area metrics");
            check(helper, (int) metric(device, QuarryIds.MINING_HEAD_LEVEL) == 3 && (double) metric(device, QuarryIds.MINING_SPEED) == 3.0,
                    "Head metrics");
            check(helper, ((Percentage) metric(device, QuarryIds.FUEL_PERCENTAGE)).value() == 20, "Fuel metric: 6400/32000");
            check(helper, ((Duration) metric(device, QuarryIds.RUNTIME_REMAINING)).ticks() == 6400, "Runtime metric");
            check(helper, (long) metric(device, QuarryIds.ESTIMATED_BLOCKS) == 106, "Estimate metric");
            check(helper, (long) metric(device, QuarryIds.OUTPUT_ITEM_COUNT) == 32, "Output items metric");
            check(helper, device.status().state() == DeviceStatus.State.ONLINE && device.status().message().isPresent(),
                    "A loaded quarry must be ONLINE (HomeCore requires ONLINE for actions) with its state as message");
            entity.headSlot().setStackInSlot(0, ItemStack.EMPTY);
            check(helper, metric(device, QuarryIds.STATUS) == QuarryStatus.NO_HEAD && device.status().state() == DeviceStatus.State.ONLINE,
                    "NO_HEAD must stay controllable (ONLINE) and show in the status metric");
            helper.succeed();
        });
    }

    /** START / PAUSE / RESUME / STOP through HomeCore's authorized gateway only. */
    @GameTest(template = "empty", batch = "homecore_c", timeoutTicks = 200)
    public static void actionsThroughTheGateway(GameTestHelper helper) {
        QuarryControllerBlockEntity entity = quarry(helper);
        for (int x = 3; x <= 6; x++) for (int z = 0; z <= 3; z++) helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
        entity.headSlot().setStackInSlot(0, new ItemStack(QuarryRegistries.MINING_HEAD_I.get()));
        entity.fuelSlot().setStackInSlot(0, new ItemStack(Items.COAL, 4));
        FakePlayer owner = player(helper, "quarry_owner");
        FakePlayer stranger = player(helper, "quarry_stranger");
        var server = helper.getLevel().getServer();
        UUID[] network = new UUID[1];
        helper.runAtTickTime(5, () -> {
            check(helper, DashboardAPI.executeAction(owner, UUID.randomUUID(), entity.deviceId(), QuarryIds.ACTION_START, fr.lkdm.homecore.api.action.Unit.INSTANCE).code()
                    == ActionResult.Code.DENIED, "Action outside a network accepted");
            network[0] = DashboardAPI.networks(server).createNetwork("Quarry test", owner.getUUID()).id();
            DashboardAPI.networks(server).addDevice(network[0], entity.deviceId());
            entity.setHomeNetwork(network[0], "Quarry test");
        });
        helper.runAtTickTime(30, () -> {
            check(helper, DashboardAPI.executeAction(stranger, network[0], entity.deviceId(), QuarryIds.ACTION_START, fr.lkdm.homecore.api.action.Unit.INSTANCE).code()
                    == ActionResult.Code.DENIED, "A non-member started the quarry");
            ActionResult start = DashboardAPI.executeAction(owner, network[0], entity.deviceId(), QuarryIds.ACTION_START, fr.lkdm.homecore.api.action.Unit.INSTANCE);
            check(helper, start.isSuccess() && entity.running(), "Start: " + start.code());
        });
        helper.runAtTickTime(60, () -> {
            check(helper, DashboardAPI.executeAction(owner, network[0], entity.deviceId(), QuarryIds.ACTION_PAUSE, fr.lkdm.homecore.api.action.Unit.INSTANCE).isSuccess()
                    && entity.status() == QuarryStatus.PAUSED, "Pause");
        });
        helper.runAtTickTime(90, () -> {
            check(helper, DashboardAPI.executeAction(owner, network[0], entity.deviceId(), QuarryIds.ACTION_RESUME, fr.lkdm.homecore.api.action.Unit.INSTANCE).isSuccess()
                    && !entity.paused(), "Resume");
        });
        helper.runAtTickTime(120, () -> {
            check(helper, DashboardAPI.executeAction(owner, network[0], entity.deviceId(), QuarryIds.ACTION_STOP, fr.lkdm.homecore.api.action.Unit.INSTANCE).isSuccess()
                    && !entity.running() && entity.area().isPresent() && entity.miningHead().isPresent(), "Stop must keep the job data");
            ActionResult again = DashboardAPI.executeAction(owner, network[0], entity.deviceId(), QuarryIds.ACTION_PAUSE, fr.lkdm.homecore.api.action.Unit.INSTANCE);
            check(helper, again.code() == ActionResult.Code.FAILED || again.code() == ActionResult.Code.RATE_LIMITED,
                    "Pausing a stopped quarry must be refused");
            DashboardAPI.networks(server).deleteNetwork(network[0]);
            helper.succeed();
        });
    }

    /** Events are published on transitions only: one fuel_low per crossing, re-armed by refuelling. */
    @GameTest(template = "empty", batch = "homecore_d", timeoutTicks = 200)
    public static void eventsOnTransitionsOnly(GameTestHelper helper) {
        QuarryControllerBlockEntity entity = quarry(helper);
        entity.headSlot().setStackInSlot(0, new ItemStack(QuarryRegistries.MINING_HEAD_I.get()));
        entity.fuelSlot().setStackInSlot(0, new ItemStack(Items.COAL, 20));
        List<DeviceEvent> received = new CopyOnWriteArrayList<>();
        var subscription = DashboardAPI.events(helper.getLevel().getServer()).subscribe(event -> {
            if (event.source().equals(entity.deviceId())) received.add(event);
        });
        helper.runAtTickTime(5, () -> {
            check(helper, entity.start() && entity.pause(), "Start then pause");
        });
        helper.runAtTickTime(10, () -> entity.fuelSlot().setStackInSlot(0, new ItemStack(Items.COAL, 2)));
        helper.runAtTickTime(40, () -> entity.fuelSlot().setStackInSlot(0, new ItemStack(Items.COAL, 1)));
        helper.runAtTickTime(70, () -> entity.fuelSlot().setStackInSlot(0, new ItemStack(Items.COAL, 20)));
        helper.runAtTickTime(100, () -> entity.fuelSlot().setStackInSlot(0, new ItemStack(Items.COAL, 1)));
        helper.runAtTickTime(110, () -> helper.setBlock(QUARRY.south(), Blocks.DROPPER));
        helper.runAtTickTime(140, () -> helper.setBlock(QUARRY.south(), Blocks.AIR));
        helper.runAtTickTime(170, () -> {
            subscription.close();
            long fuelLow = received.stream().filter(event -> event.type().equals(QuarryIds.FUEL_LOW)).count();
            check(helper, received.stream().anyMatch(event -> event.type().equals(QuarryIds.STARTED)), "No started event");
            check(helper, received.stream().anyMatch(event -> event.type().equals(QuarryIds.PAUSED)), "No paused event");
            check(helper, fuelLow == 2, "Expected exactly 2 fuel_low events (crossing, re-arm, crossing), got " + fuelLow);
            check(helper, received.stream().filter(event -> event.type().equals(QuarryIds.STORAGE_CONNECTED_EVENT)).count() == 1
                    && received.stream().filter(event -> event.type().equals(QuarryIds.STORAGE_DISCONNECTED)).count() == 1,
                    "storage_connected / storage_disconnected must fire once each");
            check(helper, received.stream().filter(event -> event.type().equals(QuarryIds.STARTED)).count() == 1, "started repeated");
            helper.succeed();
        });
    }

    /** Output full then available, as events, without spam. */
    @GameTest(template = "empty", batch = "homecore_e", timeoutTicks = 200)
    public static void outputFullEvents(GameTestHelper helper) {
        QuarryControllerBlockEntity entity = quarry(helper);
        helper.setBlock(new BlockPos(3, 1, 0), Blocks.STONE);
        entity.headSlot().setStackInSlot(0, new ItemStack(QuarryRegistries.MINING_HEAD_III.get()));
        entity.fuelSlot().setStackInSlot(0, new ItemStack(Items.COAL, 4));
        for (int slot = 0; slot < QuarryControllerBlockEntity.BUFFER_SLOTS; slot++) entity.buffer().setStackInSlot(slot, new ItemStack(Items.DIRT, 64));
        List<DeviceEvent> received = new CopyOnWriteArrayList<>();
        var subscription = DashboardAPI.events(helper.getLevel().getServer()).subscribe(event -> {
            if (event.source().equals(entity.deviceId())) received.add(event);
        });
        helper.runAtTickTime(5, () -> check(helper, entity.start(), "Start"));
        helper.runAtTickTime(80, () -> entity.buffer().setStackInSlot(0, ItemStack.EMPTY));
        helper.runAtTickTime(180, () -> {
            subscription.close();
            check(helper, received.stream().filter(event -> event.type().equals(QuarryIds.OUTPUT_FULL)).count() == 1, "output_full count");
            check(helper, received.stream().filter(event -> event.type().equals(QuarryIds.OUTPUT_AVAILABLE)).count() == 1, "output_available count");
            helper.succeed();
        });
    }
}
