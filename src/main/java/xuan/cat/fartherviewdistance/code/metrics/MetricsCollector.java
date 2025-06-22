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
 * Collects and caches metrics data to minimize performance impact
 */
public class MetricsCollector {
    private static final Map<String, Object> cachedMetrics = new ConcurrentHashMap<>();

    /**
     * Initializes the metrics collector and starts the async update task
     * 
     * @param plugin The plugin instance
     */
    public static void initialize(final Plugin plugin) {
        try {
            // Initial update
            MetricsCollector.updateCache();

            // Schedule periodic updates
            Bukkit.getScheduler().runTaskTimerAsynchronously(plugin,
                    MetricsCollector::updateCache, 1200L, 1200L); // 1 minute (1200 ticks)
        } catch (final Exception e) {
            // Ensure any initialization errors don't crash the plugin
        }
    }

    /**
     * Updates all cached metrics
     */
    private static synchronized void updateCache() {
        try {
            // Calculate average view distance
            int totalDistance = 0;
            int playerCount = 0;

            // Network speed categories
            final Map<String, AtomicInteger> networkSpeedCounts = new HashMap<>();

            // View distance categories
            final Map<String, AtomicInteger> viewDistanceCounts = new HashMap<>();

            // Network load metrics
            double totalLoadFast5s = 0;
            double totalLoadFast1m = 0;
            double totalLoadFast5m = 0;
            double totalLoadSlow5s = 0;
            double totalLoadSlow1m = 0;
            double totalLoadSlow5m = 0;
            double totalConsume5s = 0;
            double totalConsume1m = 0;
            double totalConsume5m = 0;
            double totalNetworkSpeedAVG = 0;

            try {
                for (final Player player : Bukkit.getOnlinePlayers()) {
                    try {
                        final PlayerView playerView = ViewDistance.getPlayerView(player);
                        if (playerView != null) {
                            // Count players
                            playerCount++;

                            // View distance metrics
                            final int viewDist = playerView.getNowExtendViewDistance();
                            totalDistance += viewDist;

                            // Network speed
                            final long networkSpeed = playerView.getNetworkSpeedAVG();
                            totalNetworkSpeedAVG += networkSpeed;
                            String speedCategory;

                            // Create categories in KB/s
                            final int speedKBps = (int) (networkSpeed / 1024);
                            if (speedKBps < 100)
                                speedCategory = "<100 KB/s";
                            else if (speedKBps < 500)
                                speedCategory = "100-500 KB/s";
                            else if (speedKBps < 1000)
                                speedCategory = "500-1000 KB/s";
                            else if (speedKBps < 5000)
                                speedCategory = "1-5 MB/s";
                            else
                                speedCategory = ">5 MB/s";

                            try {
                                networkSpeedCounts.computeIfAbsent(speedCategory, k -> new AtomicInteger(0))
                                        .incrementAndGet();
                            } catch (final Exception e) {
                                // Ignore map update errors
                            }

                            // View distance distribution
                            try {
                                final String distCategory = viewDist + " chunks";
                                viewDistanceCounts.computeIfAbsent(distCategory, k -> new AtomicInteger(0))
                                        .incrementAndGet();
                            } catch (final Exception e) {
                                // Ignore map update errors
                            }

                            // Network metrics - each in separate try-catch to ensure one failure doesn't
                            // affect others
                            try {
                                totalLoadFast5s += playerView.getNetworkReportLoadFast5s();
                            } catch (final Exception e) {
                            }

                            try {
                                totalLoadFast1m += playerView.getNetworkReportLoadFast1m();
                            } catch (final Exception e) {
                            }

                            try {
                                totalLoadFast5m += playerView.getNetworkReportLoadFast5m();
                            } catch (final Exception e) {
                            }

                            try {
                                totalLoadSlow5s += playerView.getNetworkReportLoadSlow5s();
                            } catch (final Exception e) {
                            }

                            try {
                                totalLoadSlow1m += playerView.getNetworkReportLoadSlow1m();
                            } catch (final Exception e) {
                            }

                            try {
                                totalLoadSlow5m += playerView.getNetworkReportLoadSlow5m();
                            } catch (final Exception e) {
                            }

                            try {
                                totalConsume5s += playerView.getNetworkReportConsume5s();
                            } catch (final Exception e) {
                            }

                            try {
                                totalConsume1m += playerView.getNetworkReportConsume1m();
                            } catch (final Exception e) {
                            }

                            try {
                                totalConsume5m += playerView.getNetworkReportConsume5m();
                            } catch (final Exception e) {
                            }
                        }
                    } catch (final Exception e) {
                        // Skip this player and continue with the next
                    }
                }
            } catch (final Exception e) {
                // Player iteration error - metrics will be based on partial data
            }

            // Store calculated averages in cache - each in its own try-catch
            try {
                MetricsCollector.cachedMetrics.put("averageViewDistance",
                        playerCount > 0 ? totalDistance / playerCount : 0);
            } catch (final Exception e) {
            }
            try {
                MetricsCollector.cachedMetrics.put("averageNetworkSpeed",
                        playerCount > 0 ? totalNetworkSpeedAVG / playerCount : 0);
            } catch (final Exception e) {
            }

            // Store network loads - each in its own try-catch
            try {
                MetricsCollector.cachedMetrics.put("loadFast5s",
                        playerCount > 0 ? (int) ((totalLoadFast5s / playerCount) * 100) : 0);
            } catch (final Exception e) {
            }

            try {
                MetricsCollector.cachedMetrics.put("loadFast1m",
                        playerCount > 0 ? (int) ((totalLoadFast1m / playerCount) * 100) : 0);
            } catch (final Exception e) {
            }

            try {
                MetricsCollector.cachedMetrics.put("loadFast5m",
                        playerCount > 0 ? (int) ((totalLoadFast5m / playerCount) * 100) : 0);
            } catch (final Exception e) {
            }

            try {
                MetricsCollector.cachedMetrics.put("loadSlow5s",
                        playerCount > 0 ? (int) ((totalLoadSlow5s / playerCount) * 100) : 0);
            } catch (final Exception e) {
            }

            try {
                MetricsCollector.cachedMetrics.put("loadSlow1m",
                        playerCount > 0 ? (int) ((totalLoadSlow1m / playerCount) * 100) : 0);
            } catch (final Exception e) {
            }

            try {
                MetricsCollector.cachedMetrics.put("loadSlow5m",
                        playerCount > 0 ? (int) ((totalLoadSlow5m / playerCount) * 100) : 0);
            } catch (final Exception e) {
            }

            // Store consumption metrics - each in its own try-catch
            try {
                MetricsCollector.cachedMetrics.put("consume5s",
                        playerCount > 0 ? (int) ((totalConsume5s / playerCount) * 100) : 0);
            } catch (final Exception e) {
            }

            try {
                MetricsCollector.cachedMetrics.put("consume1m",
                        playerCount > 0 ? (int) ((totalConsume1m / playerCount) * 100) : 0);
            } catch (final Exception e) {
            }

            try {
                MetricsCollector.cachedMetrics.put("consume5m",
                        playerCount > 0 ? (int) ((totalConsume5m / playerCount) * 100) : 0);
            } catch (final Exception e) {
            }

            // Store distributions - each in its own try-catch
            try {
                MetricsCollector.cachedMetrics.put("networkSpeedDistribution", networkSpeedCounts);
            } catch (final Exception e) {
            }

            try {
                MetricsCollector.cachedMetrics.put("viewDistanceDistribution", viewDistanceCounts);
            } catch (final Exception e) {
            }

            try {
                MetricsCollector.cachedMetrics.put("playerCount", playerCount);
            } catch (final Exception e) {
            }
        } catch (final Exception e) {
            // Catch any other unexpected errors to make the metrics collection robust
        }
    }

    /**
     * Updates the metrics cache if needed - simply uses the already updated cache
     * as it's already being updated on a regular schedule
     */
    private static void updateCacheIfNeeded() {
        try {
            // No need to check time since we're on a scheduled task
            // The cached values are already being updated regularly
        } catch (final Exception e) {
            // Prevent any unexpected errors
        }
    }

    /**
     * Gets average view distance
     * 
     * @return Average view distance across all players
     */
    public static int getAverageViewDistance() {
        return MetricsCollector.getSafeIntValue("averageViewDistance");
    }

    /**
     * Gets average network speed
     * 
     * @return Average network speed in bytes
     */
    public static int getAverageNetworkSpeed() {
        return MetricsCollector.getSafeIntValue("averageNetworkSpeed");
    }

    /**
     * Gets network load fast 5s
     * 
     * @return Load percentage (0-100)
     */
    public static int getLoadFast5s() {
        return MetricsCollector.getSafeIntValue("loadFast5s");
    }

    /**
     * Gets network load fast 1m
     * 
     * @return Load percentage (0-100)
     */
    public static int getLoadFast1m() {
        return MetricsCollector.getSafeIntValue("loadFast1m");
    }

    /**
     * Gets network load fast 5m
     * 
     * @return Load percentage (0-100)
     */
    public static int getLoadFast5m() {
        return MetricsCollector.getSafeIntValue("loadFast5m");
    }

    /**
     * Gets network load slow 5s
     * 
     * @return Load percentage (0-100)
     */
    public static int getLoadSlow5s() {
        return MetricsCollector.getSafeIntValue("loadSlow5s");
    }

    /**
     * Gets network load slow 1m
     * 
     * @return Load percentage (0-100)
     */
    public static int getLoadSlow1m() {
        return MetricsCollector.getSafeIntValue("loadSlow1m");
    }

    /**
     * Gets network load slow 5m
     * 
     * @return Load percentage (0-100)
     */
    public static int getLoadSlow5m() {
        return MetricsCollector.getSafeIntValue("loadSlow5m");
    }

    /**
     * Gets network consumption 5s
     * 
     * @return Consumption percentage (0-100)
     */
    public static int getConsume5s() {
        return MetricsCollector.getSafeIntValue("consume5s");
    }

    /**
     * Gets network consumption 1m
     * 
     * @return Consumption percentage (0-100)
     */
    public static int getConsume1m() {
        return MetricsCollector.getSafeIntValue("consume1m");
    }

    /**
     * Gets network consumption 5m
     * 
     * @return Consumption percentage (0-100)
     */
    public static int getConsume5m() {
        return MetricsCollector.getSafeIntValue("consume5m");
    }

    /**
     * Gets network speed distribution
     * 
     * @return Map of network speed categories to counts
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Map<String, Integer>> getNetworkSpeedDistribution() {
        try {
            MetricsCollector.updateCacheIfNeeded();
            final Map<String, Map<String, Integer>> result = new HashMap<>();
            final Object value = MetricsCollector.cachedMetrics.get("networkSpeedDistribution");

            if (value != null) {
                try {
                    final Map<String, AtomicInteger> distribution = (Map<String, AtomicInteger>) value;
                    final Map<String, Integer> counts = new HashMap<>();

                    for (final Map.Entry<String, AtomicInteger> entry : distribution.entrySet()) {
                        try {
                            counts.put(entry.getKey(), entry.getValue().get());
                        } catch (final Exception e) {
                            // Skip this entry if there's an error
                        }
                    }

                    result.put("Network Speed", counts);
                } catch (final ClassCastException e) {
                    // Handle invalid cache data
                }
            }

            return result;
        } catch (final Exception e) {
            return new HashMap<>(); // Return empty map on error
        }
    }

    /**
     * Gets view distance distribution
     * 
     * @return Map of view distance categories to counts
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Map<String, Integer>> getViewDistanceDistribution() {
        try {
            MetricsCollector.updateCacheIfNeeded();
            final Map<String, Map<String, Integer>> result = new HashMap<>();
            final Object value = MetricsCollector.cachedMetrics.get("viewDistanceDistribution");

            if (value != null) {
                try {
                    final Map<String, AtomicInteger> distribution = (Map<String, AtomicInteger>) value;
                    final Map<String, Integer> counts = new HashMap<>();

                    for (final Map.Entry<String, AtomicInteger> entry : distribution.entrySet()) {
                        try {
                            counts.put(entry.getKey(), entry.getValue().get());
                        } catch (final Exception e) {
                            // Skip this entry if there's an error
                        }
                    }

                    result.put("View Distance", counts);
                } catch (final ClassCastException e) {
                    // Handle invalid cache data
                }
            }

            return result;
        } catch (final Exception e) {
            return new HashMap<>(); // Return empty map on error
        }
    }

    /**
     * Safely gets a cached metric value with proper error handling
     *
     * @param key The key for the cached value
     * @return The cached value or 0 if not found or on error
     */
    private static int getSafeIntValue(final String key) {
        try {
            MetricsCollector.updateCacheIfNeeded();
            final Object value = MetricsCollector.cachedMetrics.get(key);
            return value != null ? ((Number) value).intValue() : 0;
        } catch (final Exception e) {
            return 0; // Return default value on error
        }
    }
}
