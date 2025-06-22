package xuan.cat.fartherviewdistance.code;

import java.util.Locale;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import xuan.cat.fartherviewdistance.api.ViewDistance;
import xuan.cat.fartherviewdistance.api.data.PlayerView;

public final class ChunkPlaceholder extends PlaceholderExpansion {

    private static ChunkPlaceholder imp;

    public ChunkPlaceholder() {
        super();
    }

    public static void registerPlaceholder() {
        (ChunkPlaceholder.imp = new ChunkPlaceholder()).register();
    }

    public static void unregisterPlaceholder() {
        if (ChunkPlaceholder.imp != null) {
            ChunkPlaceholder.imp.unregister();
        }
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public String getIdentifier() {
        return "viewdistance";
    }

    @Override
    public String getAuthor() {
        return "xuancat0208, maintained by EuSouVoce";
    }

    @Override
    public String getVersion() {
        return "FartherViewDistancePlaceholder 0.0.1";
    }

    @Override
    public String onRequest(
            final OfflinePlayer offlinePlayer,
            final String params) {
        if (!(offlinePlayer instanceof final Player player))
            return "-";
        final PlayerView playerView = ViewDistance.getPlayerView(player);
        if (playerView == null)
            return "-";
        return switch (params.toLowerCase(Locale.ROOT)) {
            case "delay" -> String.valueOf(playerView.getDelay());
            case "forcibly_max_distance" -> String.valueOf(playerView.getForciblyMaxDistance());
            case "max_extend_view_distance" -> String.valueOf(playerView.getMaxExtendViewDistance());
            case "now_extend_view_distance" -> String.valueOf(playerView.getNowExtendViewDistance());
            case "now_server_view_distance" -> String.valueOf(playerView.getNowServerViewDistance());
            case "forcibly_send_second_max_bytes" -> String.valueOf(playerView.getForciblySendSecondMaxBytes());
            case "network_speed_avg" -> String.valueOf(playerView.getNetworkSpeedAVG());
            case "network_report_load_fast_5s" -> String.valueOf(playerView.getNetworkReportLoadFast5s());
            case "network_report_load_fast_1m" -> String.valueOf(playerView.getNetworkReportLoadFast1m());
            case "network_report_load_fast_5m" -> String.valueOf(playerView.getNetworkReportLoadFast5m());
            case "network_report_load_slow_5s" -> String.valueOf(playerView.getNetworkReportLoadSlow5s());
            case "network_report_load_slow_1m" -> String.valueOf(playerView.getNetworkReportLoadSlow1m());
            case "network_report_load_slow_5m" -> String.valueOf(playerView.getNetworkReportLoadSlow5m());
            case "network_report_consume_5s" -> String.valueOf(playerView.getNetworkReportConsume5s());
            case "network_report_consume_1m" -> String.valueOf(playerView.getNetworkReportConsume1m());
            case "network_report_consume_5m" -> String.valueOf(playerView.getNetworkReportConsume5m());
            default -> null;
        };
    }
}