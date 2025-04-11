package xuan.cat.fartherviewdistance.api.event;

import org.bukkit.event.HandlerList;

import xuan.cat.fartherviewdistance.api.data.PlayerView;

public final class PlayerInitViewEvent extends ExtendChunkEvent {
    private static final HandlerList handlers = new HandlerList();

    public PlayerInitViewEvent(final PlayerView view) {
        super(view);
    }

    public HandlerList getHandlers() {
        return PlayerInitViewEvent.handlers;
    }

    public static HandlerList getHandlerList() {
        return PlayerInitViewEvent.handlers;
    }
}
