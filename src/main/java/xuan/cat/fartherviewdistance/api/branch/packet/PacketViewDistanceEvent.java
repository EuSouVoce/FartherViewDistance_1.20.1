package xuan.cat.fartherviewdistance.api.branch.packet;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

public final class PacketViewDistanceEvent extends PacketEvent {
    private static final HandlerList handlers = new HandlerList();

    @Override
    public HandlerList getHandlers() {
        return PacketViewDistanceEvent.handlers;
    }

    public static HandlerList getHandlerList() {
        return PacketViewDistanceEvent.handlers;
    }

    private final int viewDistance;

    public PacketViewDistanceEvent(final Player player, final int viewDistance) {
        super(player);
        this.viewDistance = viewDistance;
    }

    public int getViewDistance() {
        return this.viewDistance;
    }

}