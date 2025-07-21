package top.bearcabbage.sellingbin;

import top.bearcabbage.sellingbin.client.SellingBinModClient;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.util.Identifier;

import java.util.LinkedList;
import java.util.List;

public class ConfigSynchronizer {
    public static final Identifier CHANNEL = Identifier.of("selling-bin", "init");

    public static void server(ServerPlayNetworkHandler serverPlayNetworkHandler, MinecraftServer minecraftServer) {
        ServerPlayNetworking.send(serverPlayNetworkHandler.player, new SyncPacket(SellingBinMod.trades));
    }

    public static void client(ClientPlayNetworkHandler networkHandler, MinecraftClient client) {
        ClientPlayNetworking.registerGlobalReceiver(SyncPacket.ID, (payload, context) -> {
            context.client().execute(() -> {
                ConfigSynchronizer.sync(payload.trades, context.player());
            });
        });
    }

    private static void sync(List<Trade> trades, ClientPlayerEntity clientPlayerEntity) {
        SellingBinModClient.matches.removeIf(match -> match instanceof Trade);
        SellingBinModClient.matches.addAll(trades);
    }

    public static class SyncPacket implements CustomPayload {
        public static final Id<SyncPacket> ID = new CustomPayload.Id<>(Identifier.of("selling-bin", "sync"));
        public static final PacketCodec<PacketByteBuf, SyncPacket> CODEC = PacketCodec.of(
                SyncPacket::write,
                SyncPacket::new);

        public final List<Trade> trades;

        public SyncPacket(PacketByteBuf buf) {
            List<Trade> l = new LinkedList<>();
            int len = buf.readVarInt();
            for (int i = 0; i < len; i++) {
                Trade t = new Trade();
                t.setName(buf.readString());
                t.setSellPrice(buf.readVarInt());
                t.setSellAmount(buf.readVarInt());
                t.setColor(buf.readInt());
                l.add(t);
            }
            trades = l;
        }

        public SyncPacket(List<Trade> trades) {
            this.trades = trades;
        }

        public void write(PacketByteBuf buf) {
            buf.writeVarInt(trades.size());
            for (Trade t : trades) {
                buf.writeString(t.getName());
                buf.writeVarInt(t.getSellPrice());
                buf.writeVarInt(t.getSellAmount());
                buf.writeInt(t.getColor());
            }
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}