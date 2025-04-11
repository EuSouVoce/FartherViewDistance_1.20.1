package xuan.cat.fartherviewdistance.code.branch;

import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;
import java.util.function.Consumer;

import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

import io.netty.buffer.Unpooled;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheRadiusPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LevelLightEngine;
import xuan.cat.fartherviewdistance.api.branch.BranchChunk;
import xuan.cat.fartherviewdistance.api.branch.BranchChunkLight;
import xuan.cat.fartherviewdistance.api.branch.BranchPacket;

public final class PacketCode implements BranchPacket {
    private final PacketHandleLightUpdateCode handleLightUpdate = new PacketHandleLightUpdateCode();
    private final LevelLightEngine noOpLevelLightEngine = new LevelLightEngine(new LightChunkGetter() {
        public LightChunk getChunkForLighting(final int chunkX, final int chunkZ) {
            return null;
        }

        public BlockGetter getLevel() {
            return null;
        }
    }, false, false) {
        public int getLightSectionCount() {
            return 0;
        }
    };

    private Field chunkPacketLightDataField;

    {
        try {
            this.chunkPacketLightDataField = ClientboundLevelChunkWithLightPacket.class.getDeclaredField("lightData");
            this.chunkPacketLightDataField.setAccessible(true);
        } catch (NoSuchFieldException | SecurityException | InaccessibleObjectException e) {
            e.printStackTrace();
        }
    }

    public void sendPacket(final Player player, net.minecraft.network.protocol.Packet<?> packet) {
        try {
            final Connection container = ((CraftPlayer) player).getHandle().connection.connection;
            container.send(packet);
        } catch (final IllegalArgumentException ignored) {
        }
    }

    public void sendViewDistance(final Player player, final int viewDistance) {
        sendPacket(player, new ClientboundSetChunkCacheRadiusPacket(viewDistance));
    }

    public void sendUnloadChunk(final Player player, final int chunkX, final int chunkZ) {
        sendPacket(player, new ClientboundForgetLevelChunkPacket(new ChunkPos(chunkX, chunkZ)));
    }

    public Consumer<Player> sendChunkAndLight(final Player player, final BranchChunk chunk,
            final BranchChunkLight light,
            final boolean needTile, final Consumer<Integer> consumeTraffic) {
        final FriendlyByteBuf serializer = new FriendlyByteBuf(Unpooled.buffer().writerIndex(0));
        this.handleLightUpdate.write(serializer, (ChunkLightCode) light);
        consumeTraffic.accept(serializer.readableBytes());
        final ClientboundLightUpdatePacketData lightData = new ClientboundLightUpdatePacketData(serializer,
                chunk.getX(),
                chunk.getZ());
        final LevelChunk levelChunk = ((ChunkCode) chunk).getLevelChunk();
        final ServerPlayer serverPlayer = ((CraftPlayer) player).getHandle();
        final ClientboundLevelChunkWithLightPacket packet = new ClientboundLevelChunkWithLightPacket(levelChunk,
                this.noOpLevelLightEngine, null, null,
                levelChunk.getLevel().chunkPacketBlockController.shouldModify(serverPlayer, levelChunk));
        try {
            this.chunkPacketLightDataField.set(packet, lightData);
        } catch (IllegalArgumentException | IllegalAccessException e) {
            e.printStackTrace();
        }
        return p -> sendPacket(p, packet);
    }

    public void sendKeepAlive(final Player player, final long id) {
        sendPacket(player, new ClientboundKeepAlivePacket(id));
    }
}