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

    public PlayerChunkView(final Player player, final ConfigData configData, final ViewShape viewShape, final BranchPacket branchPacket) {
        this.player = player;
        this.configData = configData;
        this.branchPacket = branchPacket;
        this.mapView = configData.viewDistanceMode.createMap(viewShape);
        this.lastWorld = player.getWorld();
        this.syncKey = ChunkServer.random.nextLong();

        this.updateDistance();
        this.delay();

        this.mapView.setCenter(player.getLocation());

        this.viewAPI = new PlayerView(this);
        Bukkit.getPluginManager().callEvent(new PlayerInitViewEvent(this.viewAPI));
    }

    private int serverDistance() {
        return this.configData.serverViewDistance <= -1 ? (Bukkit.getViewDistance() + 1) : this.configData.serverViewDistance;
    }

    public void updateDistance() {
        this.updateDistance(false);
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
        if (forcibly || this.lastDistance != newDistance) {
            this.mapView.markOutsideWait(newDistance);
            final int gapDistance = this.lastDistance - newDistance;
            this.lastDistance = newDistance;
            this.mapView.extendDistance = newDistance;
            if (gapDistance > 0) {
                this.mapView.completedDistance.addAndGet(-gapDistance);
            }
            final PlayerSendViewDistanceEvent event = new PlayerSendViewDistanceEvent(this.viewAPI, newDistance);
            Bukkit.getPluginManager().callEvent(event);
            if (!event.isCancelled()) {
                this.branchPacket.sendViewDistance(this.player, event.getDistance());
            }
        }
    }

    private double square(final double num) {
        return num * num;
    }

    public boolean overSpeed() {
        return this.overSpeed(this.player.getLocation());
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
                        this.square(this.oldLocation.getX() - location.getX()) + this.square(this.oldLocation.getZ() - location.getZ()));
            }
            this.oldLocation = location;
            return speed > configWorld.speedingNotSend;
        }
    }

    public synchronized boolean move() {
        return this.move(this.player.getLocation());
    }

    public synchronized boolean move(final Location location) {
        return this.move(location.getBlockX() >> 4, location.getBlockZ() >> 4);
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

        if (this.player.getWorld() != this.lastWorld) {
            this.unload();
            return false;
        }

        int hitX;
        int hitZ;
        PlayerSendUnloadChunkEvent event;
        for (final long chunkKey : this.mapView.movePosition(chunkX, chunkZ)) {
            hitX = ViewMap.getX(chunkKey);
            hitZ = ViewMap.getZ(chunkKey);
            event = new PlayerSendUnloadChunkEvent(this.viewAPI, hitX, hitZ);
            Bukkit.getPluginManager().callEvent(event);
            if (!event.isCancelled())
                this.branchPacket.sendUnloadChunk(this.player, hitX, hitZ);
        }

        return true;

    }

    public void delay() {
        this.delay(System.currentTimeMillis() + this.configData.getWorld(this.lastWorld.getName()).delayBeforeSend);
    }

    public void delay(final long delayTime) {
        this.delayTime = delayTime;
    }

    /**
     * The function returns the next chunk key if it is within the world border and
     * certain conditions are met, otherwise it returns null.
     *
     * @return The method `next()` returns a `Long` value.
     */
    public Long next() {
        if (this.player.getWorld() != this.lastWorld) {
            this.unload();
            return null;
        }

        if (this.isUnload)
            return null;

        if (this.delayTime >= System.currentTimeMillis())
            return null;

        final Long chunkKey = this.mapView.get();
        if (chunkKey == null)
            return null;

        final WorldBorder worldBorder = this.lastWorld.getWorldBorder();
        final int chunkX = ViewMap.getX(chunkKey);
        final int chunkZ = ViewMap.getZ(chunkKey);
        final Location borderCenter = worldBorder.getCenter();
        final int borderSizeRadius = (int) worldBorder.getSize() / 2;
        final int borderMinX = ((borderCenter.getBlockX() - borderSizeRadius) >> 4) - 1;
        final int borderMaxX = ((borderCenter.getBlockX() + borderSizeRadius) >> 4) + 1;
        final int borderMinZ = ((borderCenter.getBlockZ() - borderSizeRadius) >> 4) - 1;
        final int borderMaxZ = ((borderCenter.getBlockZ() + borderSizeRadius) >> 4) + 1;

        return borderMinX <= chunkX && chunkX <= borderMaxX && borderMinZ <= chunkZ && chunkZ <= borderMaxZ ? chunkKey
                : null;
    }

    public void unload() {
        if (!this.isUnload) {
            this.delay();
            this.syncKey = ChunkServer.random.nextLong();
            this.isUnload = true;
            this.branchPacket.sendViewDistance(this.player, 0);
            this.branchPacket.sendViewDistance(this.player, this.mapView.extendDistance);
            this.mapView.clear();
        }
    }

    public boolean install() {
        if (this.isUnload) {
            this.delay();
            this.mapView.clear();
            this.updateDistance(true);

            this.lastWorld = this.player.getWorld();
            this.isUnload = false;
            return true;
        }
        return false;
    }

    public void send(final int x, final int z) {
        final PlayerViewMarkSendChunkEvent event = new PlayerViewMarkSendChunkEvent(this.viewAPI, x, z);
        Bukkit.getPluginManager().callEvent(event);
        if (!event.isCancelled())
            this.mapView.markSendPosition(x, z);
    }

    public void remove(final int x, final int z) {
        final PlayerViewMarkWaitChunkEvent event = new PlayerViewMarkWaitChunkEvent(this.viewAPI, x, z);
        Bukkit.getPluginManager().callEvent(event);
        if (!event.isCancelled())
            this.mapView.markWaitPosition(x, z);
    }

    /**
     * The function calculates and returns the maximum view distance for a player
     * based on various conditions and configurations.
     *
     * @return The method is returning an integer value, which represents the
     *         maximum view distance.
     */
    public int max() {
        final ConfigData.World configWorld = this.configData.getWorld(this.lastWorld.getName());
        int viewDistance = configWorld.maxViewDistance;
        final int clientViewDistance = this.player.getClientViewDistance();
        final Integer forciblyViewDistance = this.forciblyMaxDistance;

        final PlayerCheckViewDistanceEvent event = new PlayerCheckViewDistanceEvent(this.viewAPI, this.serverDistance(),
                clientViewDistance, viewDistance);
        Bukkit.getPluginManager().callEvent(event);

        if (event.getForciblyDistance() != null) {
            viewDistance = event.getForciblyDistance();
        } else if (forciblyViewDistance != null) {
            viewDistance = forciblyViewDistance;
        } else if (this.permissionsNeed || (this.configData.permissionsPeriodicMillisecondCheck != -1 && (this.permissionsCheck == null
                || this.permissionsCheck <= System.currentTimeMillis() - this.configData.permissionsPeriodicMillisecondCheck))) {
            this.permissionsNeed = false;
            this.permissionsCheck = System.currentTimeMillis();
            this.permissionsHit = null;
            // Check Permissions Node
            for (final Map.Entry<String, Integer> permissionsNodeEntry : this.configData.permissionsNodeList) {
                final int permissionViewDistance = permissionsNodeEntry.getValue();
                if (permissionViewDistance <= configWorld.maxViewDistance
                        && (this.permissionsHit == null || permissionViewDistance > this.permissionsHit)
                        && this.player.hasPermission(permissionsNodeEntry.getKey())) {
                    this.permissionsHit = permissionViewDistance;
                }
            }
        }

        if (this.permissionsHit != null)
            viewDistance = this.permissionsHit;

        if (viewDistance > clientViewDistance)
            viewDistance = clientViewDistance;
        if (viewDistance < 1)
            viewDistance = 1;

        return viewDistance;
    }

    public void clear() {
        this.mapView.clear();
    }

    public void recalculate() {
        this.mapView.markOutsideWait(this.mapView.serverDistance);
    }

    public ViewMap getMap() {
        return this.mapView;
    }

    public World getLastWorld() {
        return this.lastWorld;
    }

    public Player getPlayer() {
        return this.player;
    }

    public long getDelayTime() {
        return this.delayTime;
    }
}
