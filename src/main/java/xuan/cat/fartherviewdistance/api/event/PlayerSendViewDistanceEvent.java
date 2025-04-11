package xuan.cat.fartherviewdistance.api.event;

import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

import xuan.cat.fartherviewdistance.api.data.PlayerView;

public final class PlayerSendViewDistanceEvent extends ExtendChunkEvent implements Cancellable {
    private static final HandlerList handlers = new HandlerList();

    private boolean cancel = false;
    private int distance;

    public PlayerSendViewDistanceEvent(final PlayerView view, final int distance) {
        super(view);
        this.distance = distance;
    }

    public int getDistance() {
        return this.distance;
    }

    public void setDistance(final int distance) {
        this.distance = distance;
    }

    public boolean isCancelled() {
        return this.cancel;
    }

    public void setCancelled(final boolean cancel) {
        this.cancel = cancel;
    }

    public HandlerList getHandlers() {
        return PlayerSendViewDistanceEvent.handlers;
    }

    public static HandlerList getHandlerList() {
        return PlayerSendViewDistanceEvent.handlers;
    }
}
