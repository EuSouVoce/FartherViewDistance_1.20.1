package xuan.cat.fartherviewdistance.code.metrics;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import xuan.cat.fartherviewdistance.api.ViewDistance;
import xuan.cat.fartherviewdistance.api.data.PlayerView;

/**
 * Collects and caches metrics data to minimize performance impact.
 */
public final class MetricsCollector {

    private static final Map<String, Object> CACHE = new ConcurrentHashMap<>();
    private static long lastUpdate = 0L;

    public static void initialize(final Plugin plugin) {
        MetricsCollector.updateCache();
        Bukkit.getScheduler().runTaskTimerAsynchronously(
                plugin,
                MetricsCollector::updateCache,
                20L * 60, // 1 minuto
                20L * 60);
    }

    private static void updateCache() {
        final long now = System.currentTimeMillis();
        if (now - MetricsCollector.lastUpdate < 1_000)
            return; // 1 time per second max
        MetricsCollector.lastUpdate = now;

        int totalDistance = 0, playerCount = 0;
        double loadFast5s = 0, loadFast1m = 0, loadFast5m = 0,
                loadSlow5s = 0, loadSlow1m = 0, loadSlow5m = 0,
                consume5s = 0, consume1m = 0, consume5m = 0,
                netSpeed = 0;

        final Map<String, AtomicInteger> netCats = new HashMap<>();
        final Map<String, AtomicInteger> distCats = new HashMap<>();

        for (final Player p : Bukkit.getOnlinePlayers()) {
            final PlayerView pv = ViewDistance.getPlayerView(p);
            if (pv == null)
                continue;

            playerCount++;
            final int d = pv.getNowExtendViewDistance();
            totalDistance += d;

            final long speed = pv.getNetworkSpeedAVG();
            netSpeed += speed;
            final String cat = MetricsCollector.speedCat(speed);
            netCats.computeIfAbsent(cat, k -> new AtomicInteger()).incrementAndGet();

            distCats.computeIfAbsent(d + " chunks", k -> new AtomicInteger()).incrementAndGet();

            loadFast5s += pv.getNetworkReportLoadFast5s();
            loadFast1m += pv.getNetworkReportLoadFast1m();
            loadFast5m += pv.getNetworkReportLoadFast5m();
            loadSlow5s += pv.getNetworkReportLoadSlow5s();
            loadSlow1m += pv.getNetworkReportLoadSlow1m();
            loadSlow5m += pv.getNetworkReportLoadSlow5m();

            consume5s += pv.getNetworkReportConsume5s();
            consume1m += pv.getNetworkReportConsume1m();
            consume5m += pv.getNetworkReportConsume5m();
        }

        MetricsCollector.CACHE.put("averageViewDistance", playerCount > 0 ? totalDistance / playerCount : 0);
        MetricsCollector.CACHE.put("averageNetworkSpeed", playerCount > 0 ? (long) (netSpeed / playerCount) : 0);

        MetricsCollector.CACHE.put("loadFast5s", MetricsCollector.pc(loadFast5s, playerCount));
        MetricsCollector.CACHE.put("loadFast1m", MetricsCollector.pc(loadFast1m, playerCount));
        MetricsCollector.CACHE.put("loadFast5m", MetricsCollector.pc(loadFast5m, playerCount));
        MetricsCollector.CACHE.put("loadSlow5s", MetricsCollector.pc(loadSlow5s, playerCount));
        MetricsCollector.CACHE.put("loadSlow1m", MetricsCollector.pc(loadSlow1m, playerCount));
        MetricsCollector.CACHE.put("loadSlow5m", MetricsCollector.pc(loadSlow5m, playerCount));

        MetricsCollector.CACHE.put("consume5s", MetricsCollector.pc(consume5s, playerCount));
        MetricsCollector.CACHE.put("consume1m", MetricsCollector.pc(consume1m, playerCount));
        MetricsCollector.CACHE.put("consume5m", MetricsCollector.pc(consume5m, playerCount));

        MetricsCollector.CACHE.put("networkSpeedDistribution", new HashMap<>(netCats));
        MetricsCollector.CACHE.put("viewDistanceDistribution", new HashMap<>(distCats));
        MetricsCollector.CACHE.put("playerCount", playerCount);
    }

    // helpers
    private static int pc(final double total, final int players) {
        return players > 0 ? (int) ((total / players) * 100) : 0;
    }

    private static String speedCat(final long bytes) {
        final int kb = (int) (bytes / 1024);
        if (kb < 100)
            return "<100 KB/s";
        if (kb < 500)
            return "100-500 KB/s";
        if (kb < 1000)
            return "500-1000 KB/s";
        if (kb < 5000)
            return "1-5 MB/s";
        return ">5 MB/s";
    }

    public static int getAverageViewDistance() {
        return MetricsCollector.getInt("averageViewDistance");
    }

    public static int getAverageNetworkSpeed() {
        return MetricsCollector.getInt("averageNetworkSpeed");
    }

    public static int getLoadFast5s() {
        return MetricsCollector.getInt("loadFast5s");
    }

    public static int getLoadFast1m() {
        return MetricsCollector.getInt("loadFast1m");
    }

    public static int getLoadFast5m() {
        return MetricsCollector.getInt("loadFast5m");
    }

    public static int getLoadSlow5s() {
        return MetricsCollector.getInt("loadSlow5s");
    }

    public static int getLoadSlow1m() {
        return MetricsCollector.getInt("loadSlow1m");
    }

    public static int getLoadSlow5m() {
        return MetricsCollector.getInt("loadSlow5m");
    }

    public static int getConsume5s() {
        return MetricsCollector.getInt("consume5s");
    }

    public static int getConsume1m() {
        return MetricsCollector.getInt("consume1m");
    }

    public static int getConsume5m() {
        return MetricsCollector.getInt("consume5m");
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Map<String, Integer>> getNetworkSpeedDistribution() {
        final Map<String, AtomicInteger> src = (Map<String, AtomicInteger>) MetricsCollector.CACHE.getOrDefault("networkSpeedDistribution",
                Map.of());
        final Map<String, Integer> counts = new HashMap<>();
        src.forEach((k, v) -> counts.put(k, v.get()));
        return Map.of("Network Speed", counts);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Map<String, Integer>> getViewDistanceDistribution() {
        final Map<String, AtomicInteger> src = (Map<String, AtomicInteger>) MetricsCollector.CACHE.getOrDefault("viewDistanceDistribution",
                Map.of());
        final Map<String, Integer> counts = new HashMap<>();
        src.forEach((k, v) -> counts.put(k, v.get()));
        return Map.of("View Distance", counts);
    }

    private static int getInt(final String key) {
        final Object v = MetricsCollector.CACHE.get(key);
        return v instanceof Number n ? n.intValue() : 0;
    }
}
