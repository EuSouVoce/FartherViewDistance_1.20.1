package xuan.cat.fartherviewdistance.api.branch.packet;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

import net.minecraft.world.level.ChunkPos;

public final class PacketUnloadChunkEvent extends PacketEvent {
    private static final HandlerList handlers = new HandlerList();

    @Override
    public HandlerList getHandlers() {
        return PacketUnloadChunkEvent.handlers;
    }

    public static HandlerList getHandlerList() {
        return PacketUnloadChunkEvent.handlers;
    }

    private final int chunkX;
    private final int chunkZ;

    public PacketUnloadChunkEvent(final Player player, int chunkX, int chunkZ) {
        super(player);
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
    }

    public PacketUnloadChunkEvent(final Player player, final ChunkPos chunkPos) {
        this(player, chunkPos.x, chunkPos.z);
    }

    public int getChunkX() {
        return this.chunkX;
    }

    public int getChunkZ() {
        return this.chunkZ;
    }
}