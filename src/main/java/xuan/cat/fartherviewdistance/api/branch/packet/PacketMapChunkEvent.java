package xuan.cat.fartherviewdistance.api.branch.packet;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

public final class PacketMapChunkEvent extends PacketEvent {
    private static final HandlerList handlers = new HandlerList();

    @Override
    public HandlerList getHandlers() {
        return PacketMapChunkEvent.handlers;
    }

    public static HandlerList getHandlerList() {
        return PacketMapChunkEvent.handlers;
    }

    private final int chunkX;
    private final int chunkZ;

    public PacketMapChunkEvent(final Player player, final int chunkX, final int chunkZ) {
        super(player);
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
    }

    public int getChunkX() {
        return this.chunkX;
    }

    public int getChunkZ() {
        return this.chunkZ;
    }
}