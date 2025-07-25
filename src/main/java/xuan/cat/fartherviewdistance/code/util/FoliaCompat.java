package xuan.cat.fartherviewdistance.code.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.CompletableFuture;

public final class FoliaCompat {

    public static boolean isFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    // task runner
    public static void runAsync(JavaPlugin plugin, Runnable task) {
        if (isFolia()) {
            Bukkit.getAsyncScheduler().runNow(plugin, $ -> task.run());
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
        }
    }

    public static void runRegion(JavaPlugin plugin, Location loc, Runnable task) {
        if (isFolia()) {
            Bukkit.getRegionScheduler().run(plugin, loc, $ -> task.run());
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    public static void runGlobal(JavaPlugin plugin, Runnable task) {
        if (isFolia()) {
            Bukkit.getGlobalRegionScheduler().run(plugin, $ -> task.run());
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    public static void runTaskTimerAsync(JavaPlugin plugin, long delay, long period, Runnable task) {
        if (isFolia()) {
            Bukkit.getAsyncScheduler().runAtFixedRate(plugin, $ -> task.run(), delay * 50L, period * 50L, java.util.concurrent.TimeUnit.MILLISECONDS);
        } else {
            Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, delay, period);
        }
    }

    //  chunk isLoaded replacement
    public static CompletableFuture<Boolean> isChunkLoadedAsync(Location loc) {
        return CompletableFuture.supplyAsync(() -> {
            if (isFolia()) {
                return loc.getWorld().getChunkAtAsync(loc).isDone();
            } else {
                return loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
            }
        });
    }
}