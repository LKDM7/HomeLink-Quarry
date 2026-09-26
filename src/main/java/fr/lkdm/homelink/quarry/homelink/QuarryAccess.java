package fr.lkdm.homelink.quarry.homelink;

import fr.lkdm.homecore.api.DashboardAPI;
import fr.lkdm.homecore.api.security.Permission;
import fr.lkdm.homelink.quarry.blockentity.QuarryControllerBlockEntity;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server-side authorization, as for HomeLink Farm devices. Configure (area, depth, name, network):
 * the owner, operators and members holding HomeCore's CONFIGURE permission on the quarry's network.
 * Control (screen, start, pause, resume, stop, output): anyone who may configure, plus CONTROL members.
 */
public final class QuarryAccess {
    public static final int OPERATOR_LEVEL = 2;

    private QuarryAccess() {
    }

    public static boolean canConfigure(ServerPlayer player, QuarryControllerBlockEntity quarry) {
        if (player.hasPermissions(OPERATOR_LEVEL)) return true;
        if (quarry.owner().map(owner -> owner.equals(player.getUUID())).orElse(true)) return true;
        return quarry.homeNetwork().map(network -> DashboardAPI.hasPermission(player, network, Permission.CONFIGURE)).orElse(false);
    }

    public static boolean canControl(ServerPlayer player, QuarryControllerBlockEntity quarry) {
        if (canConfigure(player, quarry)) return true;
        return quarry.homeNetwork().map(network -> DashboardAPI.hasPermission(player, network, Permission.CONTROL)).orElse(false);
    }
}
