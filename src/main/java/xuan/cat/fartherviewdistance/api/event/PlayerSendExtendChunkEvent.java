package xuan.cat.fartherviewdistance.api.event;

import org.bukkit.World;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

import xuan.cat.fartherviewdistance.api.branch.BranchChunk;
import xuan.cat.fartherviewdistance.api.data.PlayerView;

/**
 * 發送延伸的區塊給玩家時
 */
public final class PlayerSendExtendChunkEvent extends ExtendChunkEvent implements Cancellable {
    private static final HandlerList handlers = new HandlerList();

    private boolean cancel = false;
    private final BranchChunk chunk;
    private final World world;

    public PlayerSendExtendChunkEvent(final PlayerView view, final BranchChunk chunk, final World world) {
        super(view);
        this.chunk = chunk;
        this.world = world;
    }

    public BranchChunk getChunk() {
        return this.chunk;
    }

    public World getWorld() {
        return this.world;
    }

    public boolean isCancelled() {
        return this.cancel;
    }

    public void setCancelled(final boolean cancel) {
        this.cancel = cancel;
    }

    public HandlerList getHandlers() {
        return PlayerSendExtendChunkEvent.handlers;
    }

    public static HandlerList getHandlerList() {
        return PlayerSendExtendChunkEvent.handlers;
    }
}
