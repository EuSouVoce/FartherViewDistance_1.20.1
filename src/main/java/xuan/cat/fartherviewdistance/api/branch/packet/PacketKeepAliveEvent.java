package xuan.cat.fartherviewdistance.api.branch.packet;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

public final class PacketKeepAliveEvent extends PacketEvent {
    private static final HandlerList handlers = new HandlerList();

    @Override // just to be sure.
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }

    private final long id;

    public PacketKeepAliveEvent(final Player player, final long id) {
        super(player);
        this.id = id;
    }

    public long getId() {
        return this.id;
    }
}