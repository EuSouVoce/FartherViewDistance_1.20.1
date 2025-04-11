package xuan.cat.fartherviewdistance.api.event;

import org.bukkit.event.HandlerList;

import xuan.cat.fartherviewdistance.api.data.PlayerView;

public final class PlayerCheckViewDistanceEvent extends ExtendChunkEvent {
    private static final HandlerList handlers = new HandlerList();

    private final int serverDistance;
    private final int clientDistance;
    private final int maxDistance;
    private Integer forciblyDistance;

    public PlayerCheckViewDistanceEvent(PlayerView view, int serverDistance, int clientDistance, int maxDistance) {
        super(view);
        this.serverDistance = serverDistance;
        this.clientDistance = clientDistance;
        this.maxDistance = maxDistance;
    }

    public int getClientDistance() {
        return clientDistance;
    }

    public int getMaxDistance() {
        return maxDistance;
    }

    public int getServerDistance() {
        return serverDistance;
    }

    public Integer getForciblyDistance() {
        return forciblyDistance;
    }

    public void setForciblyDistance(Integer forciblyDistance) {
        this.forciblyDistance = forciblyDistance;
    }

    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
