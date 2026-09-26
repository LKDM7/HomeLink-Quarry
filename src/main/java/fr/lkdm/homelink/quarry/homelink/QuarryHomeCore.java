package fr.lkdm.homelink.quarry.homelink;

import com.mojang.logging.LogUtils;
import fr.lkdm.homecore.api.DashboardAPI;
import fr.lkdm.homecore.api.device.DashboardDevice;
import fr.lkdm.homecore.api.network.HomeNetwork;
import fr.lkdm.homecore.api.registry.DeviceRegistry;
import fr.lkdm.homecore.api.security.Permission;
import fr.lkdm.homelink.quarry.blockentity.QuarryControllerBlockEntity;
import fr.lkdm.homelink.quarry.registry.QuarryRegistries;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

/**
 * Bridge to the public HomeCore API only. The provider is registered once; live devices are registered
 * and unregistered from the block entity lifecycle (HomeCore never scans the world).
 */
public final class QuarryHomeCore {
    private static final Logger LOGGER = LogUtils.getLogger();

    public enum BindResult { BOUND, UNBOUND, UNCHANGED, DENIED, UNKNOWN_NETWORK }

    private QuarryHomeCore() {
    }

    /** Call once during common setup. */
    public static void registerProviders() {
        DashboardAPI.registerDeviceProvider(QuarryRegistries.QUARRY_CONTROLLER.get(), quarry -> new QuarryDevice(quarry, event -> {
            if (quarry.getLevel() instanceof ServerLevel level) DashboardAPI.events(level.getServer()).publish(event);
        }));
        LOGGER.info("HomeLink Quarry registered its HomeCore device provider (HomeCore API {})", DashboardAPI.API_VERSION);
    }

    /** Registers a loaded controller as a live device, giving a copied controller its own identity. */
    public static Optional<QuarryDevice> register(ServerLevel level, QuarryControllerBlockEntity quarry) {
        DeviceRegistry registry = DashboardAPI.devices(level.getServer());
        if (registry.get(quarry.deviceId()).isPresent()) {
            LOGGER.warn("Quarry at {} duplicated device {}; assigning a new identity", quarry.getBlockPos(), quarry.deviceId());
            quarry.resetIdentityAfterCollision();
        }
        try {
            Optional<DashboardDevice> discovered = DashboardAPI.providers().discover(quarry);
            if (discovered.isEmpty() || !(discovered.get() instanceof QuarryDevice device)) return Optional.empty();
            registry.register(device);
            return Optional.of(device);
        } catch (RuntimeException rejected) {
            LOGGER.error("HomeCore rejected quarry device {}", quarry.deviceId(), rejected);
            return Optional.empty();
        }
    }

    /** Unregisters only if the registry still holds this exact instance. */
    public static void unregister(ServerLevel level, DashboardDevice device) {
        DeviceRegistry registry = DashboardAPI.devices(level.getServer());
        registry.get(device.id()).filter(current -> current == device).ifPresent(current -> registry.unregister(current.id()));
    }

    /** Networks in which this player may add or remove devices. */
    public static List<HomeNetwork> manageableNetworks(ServerPlayer player) {
        return DashboardAPI.networks(player.server).getNetworksForPlayer(player.getUUID()).stream()
                .filter(network -> DashboardAPI.hasPermission(player, network.id(), Permission.MANAGE_NETWORK)).toList();
    }

    /**
     * Attaches the quarry to a network (empty target = detach). HomeCore's network mutations are trusted
     * server calls, so the player needs rights on the quarry AND MANAGE_NETWORK on the new and previous network.
     */
    public static BindResult bind(ServerPlayer player, QuarryControllerBlockEntity quarry, Optional<UUID> target) {
        if (!QuarryAccess.canConfigure(player, quarry)) return BindResult.DENIED;
        Optional<UUID> current = quarry.homeNetwork();
        if (current.equals(target)) return BindResult.UNCHANGED;
        var networks = DashboardAPI.networks(player.server);
        Optional<HomeNetwork> destination = Optional.empty();
        if (target.isPresent()) {
            destination = networks.getNetwork(target.get());
            if (destination.isEmpty()) return BindResult.UNKNOWN_NETWORK;
            if (!DashboardAPI.hasPermission(player, target.get(), Permission.MANAGE_NETWORK)) return BindResult.DENIED;
        }
        if (current.isPresent() && networks.getNetwork(current.get()).isPresent()) {
            if (!DashboardAPI.hasPermission(player, current.get(), Permission.MANAGE_NETWORK)) return BindResult.DENIED;
            networks.removeDevice(current.get(), quarry.deviceId());
        }
        if (destination.isPresent()) {
            networks.addDevice(destination.get().id(), quarry.deviceId());
            quarry.setHomeNetwork(destination.get().id(), destination.get().name());
            return BindResult.BOUND;
        }
        quarry.clearHomeNetwork();
        return BindResult.UNBOUND;
    }

    /** The controller was destroyed: remove its identity from its network (trusted server call). */
    public static void forgetOnRemoval(ServerLevel level, QuarryControllerBlockEntity quarry) {
        quarry.homeNetwork().ifPresent(network -> {
            var networks = DashboardAPI.networks(level.getServer());
            if (networks.getNetwork(network).isPresent()) networks.removeDevice(network, quarry.deviceId());
        });
    }
}
