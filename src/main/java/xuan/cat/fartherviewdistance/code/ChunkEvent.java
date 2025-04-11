package xuan.cat.fartherviewdistance.code;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.event.world.WorldUnloadEvent;

import xuan.cat.fartherviewdistance.api.branch.BranchMinecraft;
import xuan.cat.fartherviewdistance.api.branch.BranchPacket;
import xuan.cat.fartherviewdistance.api.branch.packet.PacketKeepAliveEvent;
import xuan.cat.fartherviewdistance.api.branch.packet.PacketMapChunkEvent;
import xuan.cat.fartherviewdistance.api.branch.packet.PacketUnloadChunkEvent;
import xuan.cat.fartherviewdistance.api.branch.packet.PacketViewDistanceEvent;
import xuan.cat.fartherviewdistance.code.data.PlayerChunkView;

@SuppressWarnings("unused")
public final class ChunkEvent implements Listener {
    private final ChunkServer chunkServer;
    private final BranchPacket branchPacket;
    private final BranchMinecraft branchMinecraft;

    public ChunkEvent(final ChunkServer chunkServer, final BranchPacket branchPacket,
            final BranchMinecraft branchMinecraft) {
        this.chunkServer = chunkServer;
        this.branchPacket = branchPacket;
        this.branchMinecraft = branchMinecraft;
    }

    /**
     * @param event Player join
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void event(final PlayerJoinEvent event) {
        this.branchMinecraft.injectPlayer(event.getPlayer());
    }

    /**
     * The function unloads the view of a player from a chunk server when they
     * teleport.
     *
     * @param event The "event" parameter is of type PlayerTeleportEvent. It
     *              represents the event that occurred when a player teleports.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void event(final PlayerTeleportEvent event) {
        this.chunkServer.unloadView(event.getPlayer(), event.getFrom(), event.getTo());
    }

    /**
     * The function unloads the view of a player from a chunk server when the player
     * moves.
     *
     * @param event The "event" parameter is of type PlayerMoveEvent. It represents
     *              the event that occurred when a player moves.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void event(final PlayerMoveEvent event) {
        // chunkServer.unloadView(event.getPlayer(), event.getFrom(), event.getTo());
    }

    /**
     * The function "event" is an event handler that is triggered when a player
     * respawns, and it calls the "respawnView" method on the "chunkServer" object,
     * passing in the player as a parameter.
     *
     * @param event The "event" parameter is of type PlayerRespawnEvent. It
     *              represents the event that occurred when a player respawns in
     *              the game.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void event(final PlayerRespawnEvent event) {
        this.chunkServer.respawnView(event.getPlayer());
    }

    /**
     * The function initializes the view for a player when they join the server.
     *
     * @param event The "event" parameter is the instance of the PlayerJoinEvent
     *              class that is passed to the method when the event occurs. It
     *              contains information about the player who joined the server.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(final PlayerJoinEvent event) {
        this.chunkServer.initView(event.getPlayer());
    }

    /**
     * The function clears the view of a player from a chunk server when they quit
     * the game.
     *
     * @param event The "event" parameter is the event object that is passed to the
     *              method. In this case, it is a PlayerQuitEvent object, which
     *              represents a player quitting the server.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(final PlayerQuitEvent event) {
        this.chunkServer.clearView(event.getPlayer());
    }

    /**
     * The function initializes a world in a chunk server when a WorldInitEvent
     * occurs.
     *
     * @param event The "event" parameter is the instance of the WorldInitEvent
     *              class that is passed to the method when it is called. It
     *              contains information about the world that is being
     *              initialized.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(final WorldInitEvent event) {
        this.chunkServer.initWorld(event.getWorld());
    }

    /**
     * The function clears the world from a chunk server when a world unload event
     * occurs.
     *
     * @param event The "event" parameter is the instance of the WorldUnloadEvent
     *              class that is passed to the method when the event occurs. It
     *              contains information about the world that is being unloaded.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(final WorldUnloadEvent event) {
        this.chunkServer.clearWorld(event.getWorld());
    }

    /**
     * The function cancels the unloading of a chunk if it is being sent to a
     * player.
     *
     * @param event The "event" parameter is an instance of the
     *              PacketUnloadChunkEvent class. It represents the event that
     *              is being handled, which occurs when a player unloads a
     *              chunk.
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void on(final PacketUnloadChunkEvent event) {
        final PlayerChunkView view = this.chunkServer.getView(event.getPlayer());
        if (view.viewAPI.isChunkSend(event.getChunkX(), event.getChunkZ()))
            event.setCancelled(true);
    }

    /**
     * The function cancels the PacketViewDistanceEvent if the player's chunk view
     * distance does not match the requested view distance.
     *
     * @param event The "event" parameter is an instance of the
     *              PacketViewDistanceEvent class. It represents an event that
     *              is triggered when a player's view distance is changed.
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void on(final PacketViewDistanceEvent event) {
        final PlayerChunkView view = this.chunkServer.getView(event.getPlayer());
        final int viewDistance = event.getViewDistance();
        if (view != null && view.getMap().extendDistance != viewDistance && viewDistance != 0)
            event.setCancelled(true);
    }

    /**
     * The function handles a packet event for map chunk and calls a method on the
     * chunk server.
     *
     * @param event The "event" parameter is an instance of the PacketMapChunkEvent
     *              class. It represents an event that is triggered when a
     *              player receives a map chunk packet from the server.
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void on(final PacketMapChunkEvent event) {
        this.chunkServer.packetEvent(event.getPlayer(), event);
    }

    /**
     * The function handles a PacketKeepAliveEvent by checking if the event ID
     * matches the ping or speed ID in the PlayerChunkView's networkSpeed object,
     * and cancels the event if there is a match.
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void on(final PacketKeepAliveEvent event) {
        final long id = event.getId();
        final PlayerChunkView view = this.chunkServer.getView(event.getPlayer());
        if (view != null) {
            synchronized (view.networkSpeed) {
                if (view.networkSpeed.pingID != null && view.networkSpeed.pingID == id) {
                    view.networkSpeed.lastPing = Math.max(1,
                            (int) (System.currentTimeMillis() - view.networkSpeed.pingTimestamp));
                    view.networkSpeed.pingID = null;
                    event.setCancelled(true);
                } else if (view.networkSpeed.speedID != null && view.networkSpeed.speedID == id) {
                    view.networkSpeed
                            .add(Math.max(1, (int) (System.currentTimeMillis() - view.networkSpeed.speedTimestamp)
                                    - view.networkSpeed.lastPing), view.networkSpeed.speedConsume);
                    view.networkSpeed.speedConsume = 0;
                    view.networkSpeed.speedID = null;
                    event.setCancelled(true);
                }
            }
        }
    }
}
