package fr.lkdm.homelink.quarry.network;

import fr.lkdm.homelink.quarry.HomeLinkQuarry;
import fr.lkdm.homelink.quarry.blockentity.QuarryControllerBlockEntity;
import fr.lkdm.homelink.quarry.homelink.QuarryHomeCore;
import fr.lkdm.homelink.quarry.menu.QuarryMenu;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Screen requests (rename, HomeNetwork) and the network choices sent to the screen. The server
 * re-checks the open menu, distance and permissions for every request.
 */
public final class QuarryPayloads {
    public static final int MAX_NAME_LENGTH = 32;
    public static final int MAX_CHOICES = 32;

    private QuarryPayloads() {
    }

    /** Renames a quarry; an empty name restores the default "Quarry I/II/III". */
    public record Rename(BlockPos pos, String name) implements CustomPacketPayload {
        public static final Type<Rename> TYPE = new Type<>(HomeLinkQuarry.id("rename"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Rename> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Rename::pos, ByteBufCodecs.stringUtf8(MAX_NAME_LENGTH * 4), Rename::name, Rename::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Choice(UUID id, String name) {
        public static final StreamCodec<ByteBuf, Choice> CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, Choice::id, ByteBufCodecs.stringUtf8(128), Choice::name, Choice::new);
    }

    /** Server to client: HomeNetworks the player may attach this quarry to (MANAGE_NETWORK only). */
    public record Choices(BlockPos pos, List<Choice> choices) implements CustomPacketPayload {
        public static final Type<Choices> TYPE = new Type<>(HomeLinkQuarry.id("network_choices"));
        public static final StreamCodec<ByteBuf, Choices> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Choices::pos, Choice.CODEC.apply(ByteBufCodecs.list(MAX_CHOICES)), Choices::choices, Choices::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client to server: attach the open quarry to a network (empty = detach). */
    public record Bind(BlockPos pos, Optional<UUID> network) implements CustomPacketPayload {
        public static final Type<Bind> TYPE = new Type<>(HomeLinkQuarry.id("bind_network"));
        public static final StreamCodec<ByteBuf, Bind> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Bind::pos, ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), Bind::network, Bind::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(Rename.TYPE, Rename.CODEC, QuarryPayloads::rename);
        registrar.playToServer(Bind.TYPE, Bind.CODEC, QuarryPayloads::bind);
        registrar.playToClient(Choices.TYPE, Choices.CODEC, (payload, context) ->
                fr.lkdm.homelink.quarry.client.QuarryClientData.setChoices(payload.pos(), payload.choices()));
    }

    public static void sendNetworkChoices(ServerPlayer player, QuarryControllerBlockEntity quarry) {
        List<Choice> choices = QuarryHomeCore.manageableNetworks(player).stream().limit(MAX_CHOICES)
                .map(network -> new Choice(network.id(), network.name())).toList();
        PacketDistributor.sendToPlayer(player, new Choices(quarry.getBlockPos(), choices));
    }

    /** The request must come from the player's open screen of that quarry. */
    private static Optional<QuarryControllerBlockEntity> openQuarry(ServerPlayer player, BlockPos pos) {
        if (!(player.containerMenu instanceof QuarryMenu menu) || !menu.position().equals(pos) || !menu.stillValid(player)) return Optional.empty();
        return player.serverLevel().getBlockEntity(pos) instanceof QuarryControllerBlockEntity quarry ? Optional.of(quarry) : Optional.empty();
    }

    private static void rename(Rename payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        openQuarry(player, payload.pos()).filter(quarry -> quarry.mayConfigure(player)).ifPresent(quarry -> quarry.setCustomName(payload.name()));
    }

    private static void bind(Bind payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        openQuarry(player, payload.pos()).ifPresent(quarry -> {
            QuarryHomeCore.BindResult result = QuarryHomeCore.bind(player, quarry, payload.network());
            boolean ok = result == QuarryHomeCore.BindResult.BOUND || result == QuarryHomeCore.BindResult.UNBOUND
                    || result == QuarryHomeCore.BindResult.UNCHANGED;
            player.displayClientMessage(Component.translatable("message.homelink_quarry.network." + result.name().toLowerCase(Locale.ROOT),
                    quarry.homeNetworkName()).withStyle(ok ? ChatFormatting.GOLD : ChatFormatting.RED), true);
        });
    }
}
