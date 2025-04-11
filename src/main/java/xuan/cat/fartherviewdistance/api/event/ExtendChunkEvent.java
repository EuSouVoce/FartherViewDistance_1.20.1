package xuan.cat.fartherviewdistance.api.event;

import xuan.cat.fartherviewdistance.api.data.PlayerView;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;

public abstract class ExtendChunkEvent extends Event {
    private final PlayerView view;

    public ExtendChunkEvent(final PlayerView view) {
        super(!Bukkit.isPrimaryThread());
        this.view = view;
    }

    public PlayerView getView() {
        return this.view;
    }
}
