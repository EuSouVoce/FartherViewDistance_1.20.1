package xuan.cat.fartherviewdistance.api;

import org.bukkit.entity.Player;

import xuan.cat.fartherviewdistance.api.data.PlayerView;
import xuan.cat.fartherviewdistance.code.ChunkIndex;
import xuan.cat.fartherviewdistance.code.data.PlayerChunkView;

public final class ViewDistance {
    private ViewDistance() {
    }

    public static PlayerView getPlayerView(final Player player) {
        final PlayerChunkView view = ChunkIndex.getChunkServer().getView(player);
        return view != null ? view.viewAPI : null;
    }
}
