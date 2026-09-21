package xuan.cat.fartherviewdistance.code.branch;

import java.util.function.Consumer;

import io.papermc.paper.antixray.ChunkPacketInfo;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.level.block.state.BlockState;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

import net.minecraft.network.Connection;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import xuan.cat.fartherviewdistance.api.branch.BranchChunk;
import xuan.cat.fartherviewdistance.api.branch.BranchChunkLight;
import xuan.cat.fartherviewdistance.api.branch.BranchPacket;

public final class PacketCode implements BranchPacket {

    public void sendPacket(final Player player, final net.minecraft.network.protocol.Packet<?> packet) {
        try {
            final Connection container = ((CraftPlayer) player).getHandle().connection.connection;
            container.send(packet);
        } catch (final IllegalArgumentException ignored) {
        }
    }

    @Override
    public void sendViewDistance(final Player player, final int viewDistance) {
        this.sendPacket(player, new ClientboundSetChunkCacheRadiusPacket(viewDistance));
    }

    @Override
    public void sendUnloadChunk(final Player player, final int chunkX, final int chunkZ) {
        this.sendPacket(player, new ClientboundForgetLevelChunkPacket(new ChunkPos(chunkX, chunkZ)));
    }

    @Override
    public Consumer<Player> sendChunkAndLight(final Player player, final BranchChunk chunk,
            final BranchChunkLight light,
            final boolean needTile, final Consumer<Integer> consumeTraffic) {
        final ClientboundLightUpdatePacketData lightData = PacketHandleLightUpdateCode.createLightData((ChunkLightCode) light, consumeTraffic);
        final LevelChunk levelChunk = ((ChunkCode) chunk).getLevelChunk();
        final ServerPlayer serverPlayer = ((CraftPlayer) player).getHandle();
        final ChunkPos chunkPos = levelChunk.getPos();
        final ChunkPacketInfo<BlockState> chunkPacketInfo = levelChunk.getLevel().chunkPacketBlockController.shouldModify(serverPlayer, levelChunk)
                ? levelChunk.getLevel().chunkPacketBlockController.getChunkPacketInfo(levelChunk)
                : null;
        final ClientboundLevelChunkWithLightPacket packet = new ClientboundLevelChunkWithLightPacket(
                chunkPos.x(), chunkPos.z(),
                new ClientboundLevelChunkPacketData(levelChunk, chunkPacketInfo),
                lightData);
        return p -> this.sendPacket(p, packet);
    }

    @Override
    public void sendKeepAlive(final Player player, final long id) {
        this.sendPacket(player, new ClientboundKeepAlivePacket(id));
    }
}