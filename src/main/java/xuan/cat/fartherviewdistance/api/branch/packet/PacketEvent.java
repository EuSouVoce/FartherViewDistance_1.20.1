package xuan.cat.fartherviewdistance.api.branch.packet;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;

public abstract class PacketEvent extends Event implements Cancellable {
    private final Player player;
    private boolean cancel = false;

    public PacketEvent(final Player player) {
        super(!Bukkit.isPrimaryThread());
        this.player = player;
    }

    @Override
    public final boolean isCancelled() {
        return this.cancel;
    }

    @Override
    public final void setCancelled(final boolean cancel) {
        this.cancel = cancel;
    }

    public final Player getPlayer() {
        return this.player;
    }
}