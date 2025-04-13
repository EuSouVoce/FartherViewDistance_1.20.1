package xuan.cat.fartherviewdistance.code;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;

import xuan.cat.fartherviewdistance.api.branch.BranchChunk;
import xuan.cat.fartherviewdistance.api.branch.BranchChunkLight;
import xuan.cat.fartherviewdistance.api.branch.BranchMinecraft;
import xuan.cat.fartherviewdistance.api.branch.BranchNBT;
import xuan.cat.fartherviewdistance.api.branch.BranchPacket;
import xuan.cat.fartherviewdistance.api.branch.packet.PacketEvent;
import xuan.cat.fartherviewdistance.api.branch.packet.PacketMapChunkEvent;
import xuan.cat.fartherviewdistance.api.event.PlayerSendExtendChunkEvent;
import xuan.cat.fartherviewdistance.code.data.ConfigData;
import xuan.cat.fartherviewdistance.code.data.CumulativeReport;
import xuan.cat.fartherviewdistance.code.data.LangFiles;
import xuan.cat.fartherviewdistance.code.data.NetworkTraffic;
import xuan.cat.fartherviewdistance.code.data.PlayerChunkView;
import xuan.cat.fartherviewdistance.code.data.viewmap.ViewMap;
import xuan.cat.fartherviewdistance.code.data.viewmap.ViewShape;

/**
 * The `ChunkServer` class in Java represents a server component responsible for
 * managing chunk data,
 * player views, network traffic, and multithreading operations in a Minecraft
 * server environment.
 */
public final class ChunkServer {
    private final ConfigData configData;
    @SuppressWarnings("unused")
    private final Plugin plugin;
    private boolean running = true;
    public final BranchMinecraft branchMinecraft;
    public final BranchPacket branchPacket;
    private final Set<BukkitTask> bukkitTasks = ConcurrentHashMap.newKeySet();

    public static final Random random = new Random(); // SyncKey
    private ScheduledExecutorService multithreadedService;
    private AtomicBoolean multithreadedCanRun;

    public final Map<Player, PlayerChunkView> playersViewMap = new ConcurrentHashMap<>();

    private final NetworkTraffic serverNetworkTraffic = new NetworkTraffic();
    private final Map<World, NetworkTraffic> worldsNetworkTraffic = new ConcurrentHashMap<>();
    private List<World> lastWorldList = new ArrayList<>();

    private final AtomicInteger serverGeneratedChunk = new AtomicInteger(0);
    private final Map<World, AtomicInteger> worldsGeneratedChunk = new ConcurrentHashMap<>();

    public final CumulativeReport serverCumulativeReport = new CumulativeReport();
    public final Map<World, CumulativeReport> worldsCumulativeReport = new ConcurrentHashMap<>();
    public final Map<Integer, CumulativeReport> threadsCumulativeReport = new ConcurrentHashMap<>();

    private final Set<Runnable> waitMoveSyncQueue = ConcurrentHashMap.newKeySet();
    public final Set<Thread> threadsSet = ConcurrentHashMap.newKeySet();
    public volatile boolean globalPause = false;
    public final LangFiles lang = new LangFiles();
    private final ViewShape viewShape;

    /**
     * Constructor for ChunkServer class. Initializes configuration data, plugin,
     * and other components.
     * Sets up scheduled tasks for synchronous and asynchronous operations.
     */
    public ChunkServer(final ConfigData configData, final Plugin plugin, final ViewShape viewShape, final BranchMinecraft branchMinecraft,
            final BranchPacket branchPacket) {
        this.configData = configData;
        this.plugin = plugin;
        this.branchMinecraft = branchMinecraft;
        this.branchPacket = branchPacket;
        this.viewShape = viewShape;

        final BukkitScheduler scheduler = Bukkit.getScheduler();
        this.bukkitTasks.add(scheduler.runTaskTimer(plugin, this::tickSync, 0, 1));
        this.bukkitTasks.add(scheduler.runTaskTimerAsynchronously(plugin, this::tickAsync, 0, 1));
        this.bukkitTasks.add(scheduler.runTaskTimerAsynchronously(plugin, this::tickReport, 0, 20));
        this.reloadMultithreaded();
    }

    /**
     * Initializes a PlayerChunkView for a given player and stores it in the
     * playersViewMap.
     * 
     * @param player The player for whom the view is being initialized.
     * @return The initialized PlayerChunkView.
     */
    public PlayerChunkView initView(final Player player) {
        final PlayerChunkView view = new PlayerChunkView(player, this.configData, this.viewShape, this.branchPacket);
        this.playersViewMap.put(player, view);
        return view;
    }

    /**
     * Removes a player's view from the playersViewMap.
     * 
     * @param player The player whose view is being cleared.
     */
    public void clearView(final Player player) {
        this.playersViewMap.remove(player);
    }

    /**
     * Retrieves the PlayerChunkView associated with a given player.
     * 
     * @param player The player whose view is being retrieved.
     * @return The PlayerChunkView associated with the player.
     */
    public PlayerChunkView getView(final Player player) {
        return this.playersViewMap.get(player);
    }

    /**
     * Reloads and initializes multithreaded services for asynchronous processing.
     * Clears previous thread data and sets up new threads for handling views and
     * ticks.
     */
    public synchronized void reloadMultithreaded() {

        if (this.multithreadedCanRun != null)
            this.multithreadedCanRun.set(false);
        if (this.multithreadedService != null) {
            this.multithreadedService.shutdown();
        }
        this.threadsCumulativeReport.clear();
        this.threadsSet.clear();

        this.playersViewMap.values().forEach(view -> view.waitSend = false);

        // Create new executors
        final AtomicBoolean canRun = new AtomicBoolean(true);
        this.multithreadedCanRun = canRun;
        this.multithreadedService = Executors.newScheduledThreadPool(this.configData.asyncThreadAmount + 1);

        this.multithreadedService.schedule(() -> {
            final Thread thread = Thread.currentThread();
            thread.setName("FartherViewDistance View thread");
            thread.setPriority(3);
            this.threadsSet.add(thread);
            this.runView(canRun);
        }, 0, TimeUnit.MILLISECONDS);

        for (int index = 0; index < this.configData.asyncThreadAmount; index++) {
            final int threadNumber = index;
            final CumulativeReport threadCumulativeReport = new CumulativeReport();
            this.threadsCumulativeReport.put(index, threadCumulativeReport);
            // Each thread responds every 50 milliseconds
            this.multithreadedService.schedule(() -> {
                final Thread thread = Thread.currentThread();
                thread.setName("FartherViewDistance AsyncTick thread #" + threadNumber);
                thread.setPriority(2);
                this.threadsSet.add(thread);
                this.runThread(canRun, threadCumulativeReport);
            }, 0, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Initializes data structures for a specific world.
     * 
     * @param world The world to initialize.
     */
    public void initWorld(final World world) {
        this.worldsNetworkTraffic.put(world, new NetworkTraffic());
        this.worldsCumulativeReport.put(world, new CumulativeReport());
        this.worldsGeneratedChunk.put(world, new AtomicInteger(0));
    }

    /**
     * Clears data associated with a specific world.
     * 
     * @param world The world whose data is being cleared.
     */
    public void clearWorld(final World world) {
        this.worldsNetworkTraffic.remove(world);
        this.worldsCumulativeReport.remove(world);
        this.worldsGeneratedChunk.remove(world);
    }

    /**
     * Synchronous tick operation. Shuffles the list of worlds and processes queued
     * runnables.
     */
    private void tickSync() {
        final List<World> worldList = Bukkit.getWorlds();
        Collections.shuffle(worldList);
        this.lastWorldList = worldList;
        this.waitMoveSyncQueue.removeIf(runnable -> {
            try {
                runnable.run();
            } catch (final Exception exception) {
                exception.printStackTrace();
            }
            return true;
        });
    }

    /**
     * Asynchronous tick operation. Resets network traffic and generated chunk
     * counters.
     */
    private void tickAsync() {
        // reset all network traffic to zero
        this.serverNetworkTraffic.next();
        this.worldsNetworkTraffic.values().forEach(NetworkTraffic::next);
        this.playersViewMap.values().forEach(view -> {
            view.networkTraffic.next();
            view.networkSpeed.next();
        });
        this.serverGeneratedChunk.set(0);
        this.worldsGeneratedChunk.values().forEach(generatedChunk -> generatedChunk.set(0));
    }

    /**
     * Updates cumulative reports for the server, worlds, and players.
     */
    private void tickReport() {
        this.serverCumulativeReport.next();
        this.worldsCumulativeReport.values().forEach(CumulativeReport::next);
        this.playersViewMap.values().forEach(view -> view.cumulativeReport.next());
        this.threadsCumulativeReport.values().forEach(CumulativeReport::next);
    }

    /**
     * Main loop for updating player views. Ensures updates are performed within a
     * 50ms time frame.
     * 
     * @param canRun AtomicBoolean controlling whether the loop should continue
     *               running.
     */
    private void runView(final AtomicBoolean canRun) {
        // Main loop
        while (canRun.get()) {
            // Start time
            final long startTime = System.currentTimeMillis();

            try {
                // The view of each player
                this.playersViewMap.forEach((player, view) -> {
                    if (!view.install())
                        view.updateDistance();
                    view.moveTooFast = view.overSpeed();
                });
            } catch (final Exception exception) {
                exception.printStackTrace();
            }

            // End time
            final long endTime = System.currentTimeMillis();
            // Maximum time consumption 50 ms
            final long needSleep = 50 - (endTime - startTime);
            if (needSleep > 0) {
                try {
                    Thread.sleep(needSleep);
                } catch (final InterruptedException ignored) {
                }
            }
        }
    }

    /**
     * Processes player movements and chunk loading in a multithreaded environment.
     * 
     * @param canRun                 AtomicBoolean controlling whether the thread
     *                               should continue running.
     * @param threadCumulativeReport Cumulative report for tracking thread-specific
     *                               metrics.
     */
    private void runThread(final AtomicBoolean canRun, final CumulativeReport threadCumulativeReport) {
        while (canRun.get()) {
            final long startTime = System.currentTimeMillis();
            final long effectiveTime = startTime + 50;

            if (!this.globalPause) {
                try {
                    final List<World> worldList = this.lastWorldList;
                    final List<PlayerChunkView> viewList = Arrays
                            .asList(this.playersViewMap.values().toArray(new PlayerChunkView[0]));
                    Collections.shuffle(viewList);
                    for (final PlayerChunkView view : viewList) {
                        view.move();
                    }
                    final Map<World, List<PlayerChunkView>> worldsViews = new HashMap<>();
                    for (final PlayerChunkView view : viewList) {
                        worldsViews.computeIfAbsent(view.getLastWorld(), key -> new ArrayList<>()).add(view);
                    }

                    handleServer: {
                        for (final World world : worldList) {
                            final ConfigData.World configWorld = this.configData.getWorld(world.getName());
                            if (!configWorld.enable)
                                continue;
                            final CumulativeReport worldCumulativeReport = this.worldsCumulativeReport.get(world);
                            if (worldCumulativeReport == null)
                                continue;
                            final NetworkTraffic worldNetworkTraffic = this.worldsNetworkTraffic.get(world);
                            if (worldNetworkTraffic == null)
                                continue;
                            if (this.serverNetworkTraffic.exceed(this.configData.getServerSendTickMaxBytes()))
                                break handleServer;
                            if (worldNetworkTraffic.exceed(configWorld.getWorldSendTickMaxBytes()))
                                continue;

                            final AtomicInteger worldGeneratedChunk = this.worldsGeneratedChunk.getOrDefault(world,
                                    new AtomicInteger(Integer.MAX_VALUE));

                            handleWorld: {
                                boolean playersFull = false;
                                while (!playersFull && effectiveTime >= System.currentTimeMillis()) {
                                    playersFull = true;
                                    for (final PlayerChunkView view : worldsViews.getOrDefault(world, new ArrayList<>(0))) {
                                        if (this.serverNetworkTraffic.exceed(this.configData.getServerSendTickMaxBytes()))
                                            break handleServer;
                                        if (worldNetworkTraffic.exceed(configWorld.getWorldSendTickMaxBytes()))
                                            break handleWorld;
                                        synchronized (view.networkTraffic) {
                                            final Integer forciblySendSecondMaxBytes = view.forciblySendSecondMaxBytes;
                                            if (view.networkTraffic.exceed(forciblySendSecondMaxBytes != null
                                                    ? (int) (forciblySendSecondMaxBytes
                                                            * this.configData.playerNetworkSpeedUseDegree) / 20
                                                    : configWorld.getPlayerSendTickMaxBytes()))
                                                continue;
                                            if (this.configData.autoAdaptPlayerNetworkSpeed && view.networkTraffic
                                                    .exceed(Math.max(1, view.networkSpeed.avg() * 50)))
                                                continue;
                                        }
                                        if (view.waitSend) {
                                            playersFull = false;
                                            continue;
                                        }
                                        if (view.moveTooFast)
                                            continue;
                                        view.waitSend = true;
                                        final long syncKey = view.syncKey;
                                        final Long chunkKey = view.next();
                                        if (chunkKey == null) {
                                            view.waitSend = false;
                                            continue;
                                        }
                                        playersFull = false;
                                        final int chunkX = ViewMap.getX(chunkKey);
                                        final int chunkZ = ViewMap.getZ(chunkKey);

                                        handlePlayer: {
                                            if (!this.configData.disableFastProcess) {
                                                // Read the latest
                                                try {
                                                    if (configWorld.readServerLoadedChunk) {
                                                        final BranchChunk chunk = this.branchMinecraft
                                                                .getChunkFromMemoryCache(world, chunkX, chunkZ);
                                                        if (chunk != null) {
                                                            // Read & write
                                                            this.serverCumulativeReport.increaseLoadFast();
                                                            worldCumulativeReport.increaseLoadFast();
                                                            view.cumulativeReport.increaseLoadFast();
                                                            threadCumulativeReport.increaseLoadFast();
                                                            final List<Runnable> asyncRunnable = new ArrayList<>();
                                                            final BranchChunkLight chunkLight = this.branchMinecraft
                                                                    .fromLight(world);
                                                            final BranchNBT chunkNBT = chunk.toNBT(chunkLight, asyncRunnable);
                                                            asyncRunnable.forEach(Runnable::run);
                                                            this.sendChunk(world, configWorld, worldNetworkTraffic, view,
                                                                    chunkX, chunkZ, chunkNBT, chunkLight, syncKey,
                                                                    worldCumulativeReport, threadCumulativeReport);
                                                            break handlePlayer;
                                                        }
                                                    }
                                                } catch (NullPointerException | NoClassDefFoundError | NoSuchMethodError
                                                        | NoSuchFieldError exception) {
                                                    exception.printStackTrace();
                                                } catch (final Exception ignored) {
                                                }

                                                try {
                                                    final BranchNBT chunkNBT = this.branchMinecraft.getChunkNBTFromDisk(world,
                                                            chunkX, chunkZ);
                                                    if (chunkNBT != null && this.branchMinecraft.fromStatus(chunkNBT)
                                                            .isAbove(BranchChunk.Status.FULL)) {
                                                        this.serverCumulativeReport.increaseLoadFast();
                                                        worldCumulativeReport.increaseLoadFast();
                                                        view.cumulativeReport.increaseLoadFast();
                                                        threadCumulativeReport.increaseLoadFast();
                                                        this.sendChunk(world, configWorld, worldNetworkTraffic, view, chunkX,
                                                                chunkZ, chunkNBT,
                                                                this.branchMinecraft.fromLight(world, chunkNBT), syncKey,
                                                                worldCumulativeReport, threadCumulativeReport);
                                                        break handlePlayer;
                                                    }
                                                } catch (NullPointerException | NoClassDefFoundError | NoSuchMethodError
                                                        | NoSuchFieldError exception) {
                                                    exception.printStackTrace();
                                                } catch (final Exception ignored) {
                                                }
                                            }

                                            final boolean canGenerated = this.serverGeneratedChunk
                                                    .get() < this.configData.serverTickMaxGenerateAmount
                                                    && worldGeneratedChunk
                                                            .get() < configWorld.worldTickMaxGenerateAmount;
                                            if (canGenerated) {
                                                this.serverGeneratedChunk.incrementAndGet();
                                                worldGeneratedChunk.incrementAndGet();
                                            }

                                            try {
                                                // paper
                                                final Chunk chunk = world.getChunkAtAsync(chunkX, chunkZ, canGenerated, true)
                                                        .get();
                                                if (chunk != null) {
                                                    this.serverCumulativeReport.increaseLoadSlow();
                                                    worldCumulativeReport.increaseLoadSlow();
                                                    view.cumulativeReport.increaseLoadSlow();
                                                    threadCumulativeReport.increaseLoadSlow();
                                                    try {
                                                        final List<Runnable> asyncRunnable = new ArrayList<>();
                                                        final BranchChunkLight chunkLight = this.branchMinecraft.fromLight(world);
                                                        final BranchNBT chunkNBT = this.branchMinecraft.fromChunk(world, chunk)
                                                                .toNBT(chunkLight, asyncRunnable);
                                                        asyncRunnable.forEach(Runnable::run);
                                                        this.sendChunk(world, configWorld, worldNetworkTraffic, view, chunkX,
                                                                chunkZ, chunkNBT, chunkLight, syncKey,
                                                                worldCumulativeReport, threadCumulativeReport);
                                                        break handlePlayer;
                                                    } catch (NullPointerException | NoClassDefFoundError
                                                            | NoSuchMethodError | NoSuchFieldError exception) {
                                                        exception.printStackTrace();
                                                    } catch (final Exception ignored) {
                                                    }
                                                } else if (this.configData.serverTickMaxGenerateAmount > 0
                                                        && configWorld.worldTickMaxGenerateAmount > 0) {
                                                    view.remove(chunkX, chunkZ);
                                                    break handlePlayer;
                                                }
                                            } catch (final ExecutionException ignored) {
                                                view.remove(chunkX, chunkZ);
                                                break handlePlayer;
                                            } catch (final NoSuchMethodError methodError) {
                                                // spigot (不推薦)
                                                if (canGenerated) {
                                                    this.serverCumulativeReport.increaseLoadSlow();
                                                    worldCumulativeReport.increaseLoadSlow();
                                                    view.cumulativeReport.increaseLoadSlow();
                                                    threadCumulativeReport.increaseLoadSlow();
                                                    try {
                                                        final List<Runnable> asyncRunnable = new ArrayList<>();
                                                        final BranchChunkLight chunkLight = this.branchMinecraft.fromLight(world);
                                                        final CompletableFuture<BranchNBT> syncNBT = new CompletableFuture<>();
                                                        this.waitMoveSyncQueue
                                                                .add(() -> syncNBT
                                                                        .complete(this.branchMinecraft
                                                                                .fromChunk(world,
                                                                                        world.getChunkAt(chunkX,
                                                                                                chunkZ))
                                                                                .toNBT(chunkLight, asyncRunnable)));
                                                        final BranchNBT chunkNBT = syncNBT.get();
                                                        asyncRunnable.forEach(Runnable::run);
                                                        this.sendChunk(world, configWorld, worldNetworkTraffic, view, chunkX,
                                                                chunkZ, chunkNBT, chunkLight, syncKey,
                                                                worldCumulativeReport, threadCumulativeReport);
                                                        break handlePlayer;
                                                    } catch (NullPointerException | NoClassDefFoundError
                                                            | NoSuchMethodError | NoSuchFieldError exception) {
                                                        exception.printStackTrace();
                                                    } catch (final Exception ignored) {
                                                    }
                                                }
                                            } catch (final InterruptedException ignored) {
                                            } catch (final Exception ex) {
                                                ex.printStackTrace();
                                            }
                                        }

                                        view.waitSend = false;
                                    }

                                    try {
                                        Thread.sleep(0L);
                                    } catch (final InterruptedException ignored) {
                                    }
                                }
                            }
                        }
                    }
                } catch (final Exception exception) {
                    exception.printStackTrace();
                }
            }

            final long endTime = System.currentTimeMillis();
            final long needSleep = 50 - (endTime - startTime);
            if (needSleep > 0) {
                try {
                    Thread.sleep(needSleep);
                } catch (final InterruptedException ignored) {
                }
            }
        }
    }

    /**
     * Sends chunk and light data to a player while managing network traffic.
     * 
     * @param world                  The world where the chunk is located.
     * @param configWorld            Configuration data for the world.
     * @param worldNetworkTraffic    Network traffic data for the world.
     * @param view                   The player's chunk view.
     * @param chunkX                 X-coordinate of the chunk.
     * @param chunkZ                 Z-coordinate of the chunk.
     * @param chunkNBT               NBT data of the chunk.
     * @param chunkLight             Lighting data of the chunk.
     * @param syncKey                Synchronization key for ensuring data
     *                               consistency.
     * @param worldCumulativeReport  Cumulative report for the world.
     * @param threadCumulativeReport Cumulative report for the thread.
     */
    private void sendChunk(final World world, final ConfigData.World configWorld, final NetworkTraffic worldNetworkTraffic,
            final PlayerChunkView view, final int chunkX, final int chunkZ, final BranchNBT chunkNBT, final BranchChunkLight chunkLight, final long syncKey,
            final CumulativeReport worldCumulativeReport, final CumulativeReport threadCumulativeReport) {
        final BranchChunk chunk = this.branchMinecraft.fromChunk(world, chunkX, chunkZ, chunkNBT,
                this.configData.calculateMissingHeightMap);
        final PlayerSendExtendChunkEvent event = new PlayerSendExtendChunkEvent(view.viewAPI, chunk, world);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled())
            return;

        if (configWorld.preventXray != null && configWorld.preventXray.size() > 0) {
            for (final Map.Entry<BlockData, BlockData[]> conversionMap : configWorld.preventXray.entrySet())
                chunk.replaceAllMaterial(conversionMap.getValue(), conversionMap.getKey());
        }

        final AtomicInteger consumeTraffic = new AtomicInteger(0);
        final Consumer<Player> chunkAndLightPacket = this.branchPacket.sendChunkAndLight(view.getPlayer(), chunk, chunkLight,
                configWorld.sendTitleData, consumeTraffic::addAndGet);

        synchronized (view.networkSpeed) {
            final Location nowLoc = view.getPlayer().getLocation();
            final int nowChunkX = nowLoc.getBlockX() >> 4;
            final int nowChunkZ = nowLoc.getBlockZ() >> 4;
            final ViewMap viewMap = view.getMap();
            if (world != nowLoc.getWorld()) {
                view.getMap().markWaitPosition(chunkX, chunkZ);
                return;
            }
            if (view.getMap().isWaitPosition(chunkX, chunkZ))
                return;
            if (this.viewShape.isInsideEdge(nowChunkX, nowChunkZ, chunkX, chunkZ, viewMap.serverDistance))
                return;
            if (view.syncKey != syncKey)
                return;
            if (!this.running)
                return;

            final boolean needMeasure = this.configData.autoAdaptPlayerNetworkSpeed && ((view.networkSpeed.speedID == null
                    && view.networkSpeed.speedTimestamp + 1000 <= System.currentTimeMillis())
                    || view.networkSpeed.speedTimestamp + 30000 <= System.currentTimeMillis());
            if (needMeasure) {
                if (view.networkSpeed.speedID != null) {
                    view.networkSpeed.add(30000, 0);
                }
                final long pingID = ChunkServer.random.nextLong();
                view.networkSpeed.pingID = pingID;
                view.networkSpeed.pingTimestamp = System.currentTimeMillis();
                this.branchPacket.sendKeepAlive(view.getPlayer(), pingID);
            }

            chunkAndLightPacket.accept(view.getPlayer());
            this.serverNetworkTraffic.use(consumeTraffic.get());
            worldNetworkTraffic.use(consumeTraffic.get());
            view.networkTraffic.use(consumeTraffic.get());
            this.serverCumulativeReport.addConsume(consumeTraffic.get());
            worldCumulativeReport.addConsume(consumeTraffic.get());
            view.cumulativeReport.addConsume(consumeTraffic.get());
            threadCumulativeReport.addConsume(consumeTraffic.get());

            if (needMeasure) {
                final long speedID = ChunkServer.random.nextLong();
                view.networkSpeed.speedID = speedID;
                view.networkSpeed.speedConsume = consumeTraffic.get();
                view.networkSpeed.speedTimestamp = System.currentTimeMillis();
                this.branchPacket.sendKeepAlive(view.getPlayer(), speedID);
            }
        }
    }

    /**
     * Handles packet events related to map chunks for a specific player's chunk
     * view.
     * 
     * @param player The player associated with the event.
     * @param event  The packet event being processed.
     */
    public void packetEvent(final Player player, final PacketEvent event) {
        final PlayerChunkView view = this.getView(player);
        if (view == null)
            return;
        if (event instanceof final PacketMapChunkEvent chunkEvent) {
            view.send(chunkEvent.getChunkX(), chunkEvent.getChunkZ());
        }
    }

    /**
     * Respawns a player's view and sends a view distance packet after a delay.
     * 
     * @param player The player whose view is being respawned.
     */
    public void respawnView(final Player player) {
        final PlayerChunkView view = this.getView(player);
        if (view == null)
            return;
        view.delay();
        this.waitMoveSyncQueue.add(() -> this.branchPacket.sendViewDistance(player, view.getMap().extendDistance));
    }

    /**
     * Unloads a player's view if they move beyond a certain distance or to a
     * different world.
     * 
     * @param player The player whose view is being unloaded.
     * @param from   The player's previous location.
     * @param move   The player's new location.
     */
    public void unloadView(final Player player, final Location from, final Location move) {
        final PlayerChunkView view = this.getView(player);
        if (view == null)
            return;
        final int blockDistance = view.getMap().extendDistance << 4;
        if (from.getWorld() != move.getWorld())
            view.unload();
        else if (Math.abs(from.getX() - move.getX()) >= blockDistance
                || Math.abs(from.getZ() - move.getZ()) >= blockDistance)
            view.unload();
    }

    /**
     * The `close` function sets a flag to stop a process, cancels all Bukkit tasks,
     * and shuts down a
     * multithreaded service.
     */
    void close() {
        this.running = false;
        for (final BukkitTask task : this.bukkitTasks)
            task.cancel();
        this.multithreadedService.shutdown();
    }
}