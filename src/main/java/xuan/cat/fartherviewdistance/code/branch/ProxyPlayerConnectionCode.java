package xuan.cat.fartherviewdistance.code.branch;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheRadiusPacket;
import xuan.cat.fartherviewdistance.api.branch.packet.PacketKeepAliveEvent;
import xuan.cat.fartherviewdistance.api.branch.packet.PacketMapChunkEvent;
import xuan.cat.fartherviewdistance.api.branch.packet.PacketUnloadChunkEvent;
import xuan.cat.fartherviewdistance.api.branch.packet.PacketViewDistanceEvent;

public final class ProxyPlayerConnectionCode {
    public static boolean read(final Player player, final Packet<?> packet) {
        if (packet instanceof ServerboundKeepAlivePacket) {
            final PacketKeepAliveEvent event = new PacketKeepAliveEvent(player,
                    ((ServerboundKeepAlivePacket) packet).getId());
            Bukkit.getPluginManager().callEvent(event);
            return !event.isCancelled();
        } else {
            return true;
        }
    }

    public static boolean write(final Player player, final Packet<?> packet) {
        try {
            if (packet instanceof final ClientboundForgetLevelChunkPacket clientboundForgetLevelChunkPacket) {
                final PacketUnloadChunkEvent event = new PacketUnloadChunkEvent(player,
                        clientboundForgetLevelChunkPacket.pos());
                Bukkit.getPluginManager().callEvent(event);
                return !event.isCancelled();
            } else if (packet instanceof final ClientboundSetChunkCacheRadiusPacket clientboundSetChunkCacheRadiusPacket) {
                final PacketViewDistanceEvent event = new PacketViewDistanceEvent(player,
                        clientboundSetChunkCacheRadiusPacket.getRadius());
                Bukkit.getPluginManager().callEvent(event);
                return !event.isCancelled();
            } else if (packet instanceof final ClientboundLevelChunkWithLightPacket clientboundLevelChunkWithLightPacket) {
                final PacketMapChunkEvent event = new PacketMapChunkEvent(player,
                        clientboundLevelChunkWithLightPacket.getX(), clientboundLevelChunkWithLightPacket.getZ());
                Bukkit.getPluginManager().callEvent(event);
                return !event.isCancelled();
            }

            return true;

        } catch (final Exception ex) {
            ex.printStackTrace();
            return true;
        }
    }
}
