package xuan.cat.fartherviewdistance.api.event;

import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

import xuan.cat.fartherviewdistance.api.data.PlayerView;

public final class PlayerViewMarkWaitChunkEvent extends ExtendChunkEvent implements Cancellable {
    private static final HandlerList handlers = new HandlerList();

    private boolean cancel = false;
    private final int chunkX;
    private final int chunkZ;

    public PlayerViewMarkWaitChunkEvent(final PlayerView view, final int chunkX, final int chunkZ) {
        super(view);
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
    }

    public int getChunkX() {
        return this.chunkX;
    }

    public int getChunkZ() {
        return this.chunkZ;
    }

    public boolean isCancelled() {
        return this.cancel;
    }

    public void setCancelled(final boolean cancel) {
        this.cancel = cancel;
    }

    public HandlerList getHandlers() {
        return PlayerViewMarkWaitChunkEvent.handlers;
    }

    public static HandlerList getHandlerList() {
        return PlayerViewMarkWaitChunkEvent.handlers;
    }
}
