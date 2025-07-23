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
        updateCache();
        Bukkit.getScheduler().runTaskTimerAsynchronously(
                plugin,
                MetricsCollector::updateCache,
                20L * 60,   // 1 minuto
                20L * 60
        );
    }

    private static void updateCache() {
        final long now = System.currentTimeMillis();
        if (now - lastUpdate < 1_000) return; // 1 time per second max
        lastUpdate = now;

        int totalDistance = 0, playerCount = 0;
        double loadFast5s = 0, loadFast1m = 0, loadFast5m = 0,
                loadSlow5s = 0, loadSlow1m = 0, loadSlow5m = 0,
                consume5s  = 0, consume1m  = 0, consume5m  = 0,
                netSpeed   = 0;

        final Map<String, AtomicInteger> netCats = new HashMap<>();
        final Map<String, AtomicInteger> distCats = new HashMap<>();

        for (final Player p : Bukkit.getOnlinePlayers()) {
            final PlayerView pv = ViewDistance.getPlayerView(p);
            if (pv == null) continue;

            playerCount++;
            final int d = pv.getNowExtendViewDistance();
            totalDistance += d;

            final long speed = pv.getNetworkSpeedAVG();
            netSpeed += speed;
            String cat = speedCat(speed);
            netCats.computeIfAbsent(cat, k -> new AtomicInteger()).incrementAndGet();

            distCats.computeIfAbsent(d + " chunks", k -> new AtomicInteger()).incrementAndGet();

            loadFast5s += pv.getNetworkReportLoadFast5s();
            loadFast1m += pv.getNetworkReportLoadFast1m();
            loadFast5m += pv.getNetworkReportLoadFast5m();
            loadSlow5s += pv.getNetworkReportLoadSlow5s();
            loadSlow1m += pv.getNetworkReportLoadSlow1m();
            loadSlow5m += pv.getNetworkReportLoadSlow5m();

            consume5s  += pv.getNetworkReportConsume5s();
            consume1m  += pv.getNetworkReportConsume1m();
            consume5m  += pv.getNetworkReportConsume5m();
        }

        CACHE.put("averageViewDistance", playerCount > 0 ? totalDistance / playerCount : 0);
        CACHE.put("averageNetworkSpeed", playerCount > 0 ? (long) (netSpeed / playerCount) : 0);

        CACHE.put("loadFast5s",  pc(loadFast5s,  playerCount));
        CACHE.put("loadFast1m",  pc(loadFast1m,  playerCount));
        CACHE.put("loadFast5m",  pc(loadFast5m,  playerCount));
        CACHE.put("loadSlow5s",  pc(loadSlow5s,  playerCount));
        CACHE.put("loadSlow1m",  pc(loadSlow1m,  playerCount));
        CACHE.put("loadSlow5m",  pc(loadSlow5m,  playerCount));

        CACHE.put("consume5s",   pc(consume5s,   playerCount));
        CACHE.put("consume1m",   pc(consume1m,   playerCount));
        CACHE.put("consume5m",   pc(consume5m,   playerCount));

        CACHE.put("networkSpeedDistribution", new HashMap<>(netCats));
        CACHE.put("viewDistanceDistribution", new HashMap<>(distCats));
        CACHE.put("playerCount", playerCount);
    }

    // helpers
    private static int pc(double total, int players) {
        return players > 0 ? (int) ((total / players) * 100) : 0;
    }

    private static String speedCat(long bytes) {
        int kb = (int) (bytes / 1024);
        if (kb < 100)        return "<100 KB/s";
        if (kb < 500)        return "100-500 KB/s";
        if (kb < 1000)       return "500-1000 KB/s";
        if (kb < 5000)       return "1-5 MB/s";
        return ">5 MB/s";
    }

    public static int getAverageViewDistance()           { return getInt("averageViewDistance"); }
    public static int getAverageNetworkSpeed()           { return getInt("averageNetworkSpeed"); }
    public static int getLoadFast5s()                    { return getInt("loadFast5s"); }
    public static int getLoadFast1m()                    { return getInt("loadFast1m"); }
    public static int getLoadFast5m()                    { return getInt("loadFast5m"); }
    public static int getLoadSlow5s()                    { return getInt("loadSlow5s"); }
    public static int getLoadSlow1m()                    { return getInt("loadSlow1m"); }
    public static int getLoadSlow5m()                    { return getInt("loadSlow5m"); }
    public static int getConsume5s()                     { return getInt("consume5s"); }
    public static int getConsume1m()                     { return getInt("consume1m"); }
    public static int getConsume5m()                     { return getInt("consume5m"); }

    @SuppressWarnings("unchecked")
    public static Map<String, Map<String, Integer>> getNetworkSpeedDistribution() {
        Map<String, AtomicInteger> src =
                (Map<String, AtomicInteger>) CACHE.getOrDefault("networkSpeedDistribution", Map.of());
        Map<String, Integer> counts = new HashMap<>();
        src.forEach((k, v) -> counts.put(k, v.get()));
        return Map.of("Network Speed", counts);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Map<String, Integer>> getViewDistanceDistribution() {
        Map<String, AtomicInteger> src =
                (Map<String, AtomicInteger>) CACHE.getOrDefault("viewDistanceDistribution", Map.of());
        Map<String, Integer> counts = new HashMap<>();
        src.forEach((k, v) -> counts.put(k, v.get()));
        return Map.of("View Distance", counts);
    }

    private static int getInt(String key) {
        Object v = CACHE.get(key);
        return v instanceof Number ? ((Number) v).intValue() : 0;
    }
}
