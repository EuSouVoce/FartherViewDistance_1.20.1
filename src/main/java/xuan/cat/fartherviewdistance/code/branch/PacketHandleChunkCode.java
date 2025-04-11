package xuan.cat.fartherviewdistance.code.branch;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftServer;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;

public final class PacketHandleChunkCode {
    public PacketHandleChunkCode() {
    }

    public void write(final RegistryFriendlyByteBuf serializer, final LevelChunk chunk, final boolean needTile) {
        final CompoundTag heightmapsNBT = new CompoundTag();
        for (final Map.Entry<Heightmap.Types, Heightmap> entry : chunk.getHeightmaps()) {
            final Heightmap.Types heightType = entry.getKey();
            final Heightmap heightMap = entry.getValue();
            if (heightType.sendToClient())
                heightmapsNBT.put(heightType.getSerializationKey(), new LongArrayTag(heightMap.getRawData()));
        }

        int chunkSize = 0;
        for (final LevelChunkSection section : chunk.getSections()) {
            chunkSize += section.getSerializedSize();
        }
        final byte[] bufferBytes = new byte[chunkSize];
        final CraftServer server = (CraftServer) Bukkit.getServer();
        final RegistryFriendlyByteBuf bufferByteBuf = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(bufferBytes),
                server.getServer().registryAccess());
        bufferByteBuf.writerIndex(0);
        for (final LevelChunkSection section : chunk.getSections()) {
            section.write(bufferByteBuf);
        }

        serializer.writeNbt(heightmapsNBT);
        serializer.writeVarInt(bufferBytes.length);
        serializer.writeBytes(bufferBytes);

        final Map<BlockPos, BlockEntity> blockEntityMap = !needTile ? new HashMap<>(0) : chunk.getBlockEntities();
        serializer.writeCollection(blockEntityMap.entrySet(), (buf, entry) -> {
            final BlockEntity blockEntity = entry.getValue();
            final CompoundTag entityNBT = blockEntity.getUpdateTag(chunk.getLevel().registryAccess());
            final BlockPos blockPos = blockEntity.getBlockPos();
            buf.writeByte(
                    SectionPos.sectionRelative(blockPos.getX()) << 4 | SectionPos.sectionRelative(blockPos.getZ()));
            buf.writeShort(blockPos.getY());
            buf.writeVarInt(BuiltInRegistries.BLOCK_ENTITY_TYPE.getId(blockEntity.getType()));
            buf.writeNbt(entityNBT.isEmpty() ? null : entityNBT);
        });
    }
}
