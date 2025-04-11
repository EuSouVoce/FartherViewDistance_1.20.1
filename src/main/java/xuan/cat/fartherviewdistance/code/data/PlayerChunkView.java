package xuan.cat.fartherviewdistance.code.data;

import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;

import xuan.cat.fartherviewdistance.api.branch.BranchPacket;
import xuan.cat.fartherviewdistance.api.data.PlayerView;
import xuan.cat.fartherviewdistance.api.event.PlayerCheckViewDistanceEvent;
import xuan.cat.fartherviewdistance.api.event.PlayerInitViewEvent;
import xuan.cat.fartherviewdistance.api.event.PlayerSendUnloadChunkEvent;
import xuan.cat.fartherviewdistance.api.event.PlayerSendViewDistanceEvent;
import xuan.cat.fartherviewdistance.api.event.PlayerViewMarkSendChunkEvent;
import xuan.cat.fartherviewdistance.api.event.PlayerViewMarkWaitChunkEvent;
import xuan.cat.fartherviewdistance.code.ChunkServer;
import xuan.cat.fartherviewdistance.code.data.viewmap.ViewMap;
import xuan.cat.fartherviewdistance.code.data.viewmap.ViewShape;

@SuppressWarnings("unused")
public final class PlayerChunkView {
    public final PlayerView viewAPI;
    private final Player player;
    private final BranchPacket branchPacket;
    /** View Calculator */
    private final ViewMap mapView;
    /** Forced Visual Field Distance */
    public Integer forciblyMaxDistance = null;
    /** Compulsory data transfer per second (in bytes) */
    public Integer forciblySendSecondMaxBytes = null;
    /** Final View Distance */
    private int lastDistance = 0;
    private final ConfigData configData;
    /** Delayed timestamp */
    private long delayTime;
    /** Unloaded */
    private boolean isUnload = false;
    /** Player's last world */
    private World lastWorld;
    /** Old location */
    private Location oldLocation = null;
    /** Whether moved to ofast */
    public volatile boolean moveTooFast = false;
    /** Network traffic monitor */
    public final NetworkTraffic networkTraffic = new NetworkTraffic();
    /** Network speed */
    public final NetworkSpeed networkSpeed = new NetworkSpeed();
    /** Waiting to send */
    public volatile boolean waitSend = false;
    /** Sync key */
    public volatile long syncKey;
    /** Create report */
    public final CumulativeReport cumulativeReport = new CumulativeReport();
    /** Check permission */
    private Long permissionsCheck = null;
    /** permission hit */
    private Integer permissionsHit = null;
    /** Permissions needed to be checked */
    public boolean permissionsNeed = true;

    public PlayerChunkView(Player player, ConfigData configData, ViewShape viewShape, BranchPacket branchPacket) {
        this.player = player;
        this.configData = configData;
        this.branchPacket = branchPacket;
        this.mapView = configData.viewDistanceMode.createMap(viewShape);
        this.lastWorld = player.getWorld();
        this.syncKey = ChunkServer.random.nextLong();

        updateDistance();
        delay();

        mapView.setCenter(player.getLocation());

        this.viewAPI = new PlayerView(this);
        Bukkit.getPluginManager().callEvent(new PlayerInitViewEvent(viewAPI));
    }

    private int serverDistance() {
        return configData.serverViewDistance <= -1 ? (Bukkit.getViewDistance() + 1) : configData.serverViewDistance;
    }

    public void updateDistance() {
        updateDistance(false);
    }

    /**
     * The function updates the distance for a map view and sends a packet to the
     * player if the distance has changed.
     *
     * @param forcibly The `forcibly` parameter is a boolean flag that indicates
     *                 whether the distance update should be forced, regardless
     *                 of whether the distance has changed or not. If `forcibly`
     *                 is set to `true`, the distance update will always be
     *                 performed. If `forcibly` is set to `false`,
     */
    private void updateDistance(final boolean forcibly) {
        int newDistance = this.max();
        synchronized (this.mapView) {
            this.mapView.serverDistance = this.serverDistance();
            if (newDistance < this.mapView.serverDistance) {
                newDistance = this.mapView.serverDistance;
            }
        }
        if (forcibly || lastDistance != newDistance) {
            mapView.markOutsideWait(newDistance);
            int gapDistance = lastDistance - newDistance;
            lastDistance = newDistance;
            mapView.extendDistance = newDistance;
            if (gapDistance > 0) {
                mapView.completedDistance.addAndGet(-gapDistance);
            }
            PlayerSendViewDistanceEvent event = new PlayerSendViewDistanceEvent(viewAPI, newDistance);
            Bukkit.getPluginManager().callEvent(event);
            if (!event.isCancelled()) {
                branchPacket.sendViewDistance(player, event.getDistance());
            }
        }
    }

    private double square(double num) {
        return num * num;
    }

    public boolean overSpeed() {
        return overSpeed(player.getLocation());
    }

    /**
     * The function checks if the speed between the current location and the
     * previous location is greater than a specified threshold.
     *
     * @param location The "location" parameter represents the current location of
     *                 an object or entity in a specific world.
     * @return The method is returning a boolean value.
     */
    public boolean overSpeed(final Location location) {
        final ConfigData.World configWorld = this.configData.getWorld(this.lastWorld.getName());
        if (configWorld.speedingNotSend == -1.0D) {
            return false;
        } else {
            double speed = 0.0D;
            if (this.oldLocation != null && this.oldLocation.getWorld() == location.getWorld()) {
                speed = Math.sqrt(
                        square(oldLocation.getX() - location.getX()) + square(oldLocation.getZ() - location.getZ()));
            }
            this.oldLocation = location;
            return speed > configWorld.speedingNotSend;
        }
    }

    public synchronized boolean move() {
        return move(player.getLocation());
    }

    public synchronized boolean move(Location location) {
        return move(location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }

    /**
     * The function moves the player to a new chunk in the game world and unloads
     * any previously loaded chunks if necessary.
     *
     * @param chunkX The X coordinate of the chunk to move to.
     * @param chunkZ The parameter `chunkZ` represents the Z-coordinate of the
     *               chunk.
     * @return The method is returning a boolean value.
     */
    public synchronized boolean move(final int chunkX, final int chunkZ) {
        if (this.isUnload)
            return false;

        if (player.getWorld() != lastWorld) {
            unload();
            return false;
        }

        int hitX;
        int hitZ;
        PlayerSendUnloadChunkEvent event;
        for (long chunkKey : mapView.movePosition(chunkX, chunkZ)) {
            hitX = ViewMap.getX(chunkKey);
            hitZ = ViewMap.getZ(chunkKey);
            event = new PlayerSendUnloadChunkEvent(viewAPI, hitX, hitZ);
            Bukkit.getPluginManager().callEvent(event);
            if (!event.isCancelled())
                branchPacket.sendUnloadChunk(player, hitX, hitZ);
        }

        return true;

    }

    public void delay() {
        delay(System.currentTimeMillis() + configData.getWorld(lastWorld.getName()).delayBeforeSend);
    }

    public void delay(long delayTime) {
        this.delayTime = delayTime;
    }

    /**
     * The function returns the next chunk key if it is within the world border and
     * certain conditions are met, otherwise it returns null.
     *
     * @return The method `next()` returns a `Long` value.
     */
    public Long next() {
        if (player.getWorld() != lastWorld) {
            unload();
            return null;
        }

        if (isUnload)
            return null;

        if (delayTime >= System.currentTimeMillis())
            return null;

        Long chunkKey = mapView.get();
        if (chunkKey == null)
            return null;

        WorldBorder worldBorder = lastWorld.getWorldBorder();
        int chunkX = ViewMap.getX(chunkKey);
        int chunkZ = ViewMap.getZ(chunkKey);
        Location borderCenter = worldBorder.getCenter();
        int borderSizeRadius = (int) worldBorder.getSize() / 2;
        int borderMinX = ((borderCenter.getBlockX() - borderSizeRadius) >> 4) - 1;
        int borderMaxX = ((borderCenter.getBlockX() + borderSizeRadius) >> 4) + 1;
        int borderMinZ = ((borderCenter.getBlockZ() - borderSizeRadius) >> 4) - 1;
        int borderMaxZ = ((borderCenter.getBlockZ() + borderSizeRadius) >> 4) + 1;

        return borderMinX <= chunkX && chunkX <= borderMaxX && borderMinZ <= chunkZ && chunkZ <= borderMaxZ ? chunkKey
                : null;
    }

    public void unload() {
        if (!isUnload) {
            delay();
            syncKey = ChunkServer.random.nextLong();
            isUnload = true;
            branchPacket.sendViewDistance(player, 0);
            branchPacket.sendViewDistance(player, mapView.extendDistance);
            mapView.clear();
        }
    }

    public boolean install() {
        if (isUnload) {
            delay();
            mapView.clear();
            updateDistance(true);

            lastWorld = player.getWorld();
            isUnload = false;
            return true;
        }
        return false;
    }

    public void send(int x, int z) {
        PlayerViewMarkSendChunkEvent event = new PlayerViewMarkSendChunkEvent(viewAPI, x, z);
        Bukkit.getPluginManager().callEvent(event);
        if (!event.isCancelled())
            mapView.markSendPosition(x, z);
    }

    public void remove(int x, int z) {
        PlayerViewMarkWaitChunkEvent event = new PlayerViewMarkWaitChunkEvent(viewAPI, x, z);
        Bukkit.getPluginManager().callEvent(event);
        if (!event.isCancelled())
            mapView.markWaitPosition(x, z);
    }

    /**
     * The function calculates and returns the maximum view distance for a player
     * based on various conditions and configurations.
     *
     * @return The method is returning an integer value, which represents the
     *         maximum view distance.
     */
    public int max() {
        ConfigData.World configWorld = configData.getWorld(lastWorld.getName());
        int viewDistance = configWorld.maxViewDistance;
        int clientViewDistance = player.getClientViewDistance();
        Integer forciblyViewDistance = forciblyMaxDistance;

        PlayerCheckViewDistanceEvent event = new PlayerCheckViewDistanceEvent(viewAPI, serverDistance(),
                clientViewDistance, viewDistance);
        Bukkit.getPluginManager().callEvent(event);

        if (event.getForciblyDistance() != null) {
            viewDistance = event.getForciblyDistance();
        } else if (forciblyViewDistance != null) {
            viewDistance = forciblyViewDistance;
        } else if (permissionsNeed || (configData.permissionsPeriodicMillisecondCheck != -1 && (permissionsCheck == null
                || permissionsCheck <= System.currentTimeMillis() - configData.permissionsPeriodicMillisecondCheck))) {
            permissionsNeed = false;
            permissionsCheck = System.currentTimeMillis();
            permissionsHit = null;
            // Check Permissions Node
            for (Map.Entry<String, Integer> permissionsNodeEntry : configData.permissionsNodeList) {
                int permissionViewDistance = permissionsNodeEntry.getValue();
                if (permissionViewDistance <= configWorld.maxViewDistance
                        && (permissionsHit == null || permissionViewDistance > permissionsHit)
                        && player.hasPermission(permissionsNodeEntry.getKey())) {
                    permissionsHit = permissionViewDistance;
                }
            }
        }

        if (permissionsHit != null)
            viewDistance = permissionsHit;

        if (viewDistance > clientViewDistance)
            viewDistance = clientViewDistance;
        if (viewDistance < 1)
            viewDistance = 1;

        return viewDistance;
    }

    public void clear() {
        mapView.clear();
    }

    public void recalculate() {
        mapView.markOutsideWait(mapView.serverDistance);
    }

    public ViewMap getMap() {
        return mapView;
    }

    public World getLastWorld() {
        return lastWorld;
    }

    public Player getPlayer() {
        return player;
    }

    public long getDelayTime() {
        return delayTime;
    }
}
