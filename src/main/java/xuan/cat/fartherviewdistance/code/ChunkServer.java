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
    public ChunkServer(ConfigData configData, Plugin plugin, ViewShape viewShape, BranchMinecraft branchMinecraft,
            BranchPacket branchPacket) {
        this.configData = configData;
        this.plugin = plugin;
        this.branchMinecraft = branchMinecraft;
        this.branchPacket = branchPacket;
        this.viewShape = viewShape;

        BukkitScheduler scheduler = Bukkit.getScheduler();
        bukkitTasks.add(scheduler.runTaskTimer(plugin, this::tickSync, 0, 1));
        bukkitTasks.add(scheduler.runTaskTimerAsynchronously(plugin, this::tickAsync, 0, 1));
        bukkitTasks.add(scheduler.runTaskTimerAsynchronously(plugin, this::tickReport, 0, 20));
        reloadMultithreaded();
    }

    /**
     * Initializes a PlayerChunkView for a given player and stores it in the
     * playersViewMap.
     * 
     * @param player The player for whom the view is being initialized.
     * @return The initialized PlayerChunkView.
     */
    public PlayerChunkView initView(Player player) {
        PlayerChunkView view = new PlayerChunkView(player, configData, viewShape, branchPacket);
        playersViewMap.put(player, view);
        return view;
    }

    /**
     * Removes a player's view from the playersViewMap.
     * 
     * @param player The player whose view is being cleared.
     */
    public void clearView(Player player) {
        playersViewMap.remove(player);
    }

    /**
     * Retrieves the PlayerChunkView associated with a given player.
     * 
     * @param player The player whose view is being retrieved.
     * @return The PlayerChunkView associated with the player.
     */
    public PlayerChunkView getView(Player player) {
        return playersViewMap.get(player);
    }

    /**
     * Reloads and initializes multithreaded services for asynchronous processing.
     * Clears previous thread data and sets up new threads for handling views and
     * ticks.
     */
    public synchronized void reloadMultithreaded() {

        if (multithreadedCanRun != null)
            multithreadedCanRun.set(false);
        if (multithreadedService != null) {
            multithreadedService.shutdown();
        }
        threadsCumulativeReport.clear();
        threadsSet.clear();

        playersViewMap.values().forEach(view -> view.waitSend = false);

        // Create new executors
        AtomicBoolean canRun = new AtomicBoolean(true);
        multithreadedCanRun = canRun;
        multithreadedService = Executors.newScheduledThreadPool(configData.asyncThreadAmount + 1);

        multithreadedService.schedule(() -> {
            Thread thread = Thread.currentThread();
            thread.setName("FartherViewDistance View thread");
            thread.setPriority(3);
            threadsSet.add(thread);
            runView(canRun);
        }, 0, TimeUnit.MILLISECONDS);

        for (int index = 0; index < configData.asyncThreadAmount; index++) {
            int threadNumber = index;
            CumulativeReport threadCumulativeReport = new CumulativeReport();
            threadsCumulativeReport.put(index, threadCumulativeReport);
            // Each thread responds every 50 milliseconds
            multithreadedService.schedule(() -> {
                Thread thread = Thread.currentThread();
                thread.setName("FartherViewDistance AsyncTick thread #" + threadNumber);
                thread.setPriority(2);
                threadsSet.add(thread);
                runThread(canRun, threadCumulativeReport);
            }, 0, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Initializes data structures for a specific world.
     * 
     * @param world The world to initialize.
     */
    public void initWorld(World world) {
        worldsNetworkTraffic.put(world, new NetworkTraffic());
        worldsCumulativeReport.put(world, new CumulativeReport());
        worldsGeneratedChunk.put(world, new AtomicInteger(0));
    }

    /**
     * Clears data associated with a specific world.
     * 
     * @param world The world whose data is being cleared.
     */
    public void clearWorld(World world) {
        worldsNetworkTraffic.remove(world);
        worldsCumulativeReport.remove(world);
        worldsGeneratedChunk.remove(world);
    }

    /**
     * Synchronous tick operation. Shuffles the list of worlds and processes queued
     * runnables.
     */
    private void tickSync() {
        List<World> worldList = Bukkit.getWorlds();
        Collections.shuffle(worldList);
        lastWorldList = worldList;
        waitMoveSyncQueue.removeIf(runnable -> {
            try {
                runnable.run();
            } catch (Exception exception) {
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
        serverNetworkTraffic.next();
        worldsNetworkTraffic.values().forEach(NetworkTraffic::next);
        playersViewMap.values().forEach(view -> {
            view.networkTraffic.next();
            view.networkSpeed.next();
        });
        serverGeneratedChunk.set(0);
        worldsGeneratedChunk.values().forEach(generatedChunk -> generatedChunk.set(0));
    }

    /**
     * Updates cumulative reports for the server, worlds, and players.
     */
    private void tickReport() {
        serverCumulativeReport.next();
        worldsCumulativeReport.values().forEach(CumulativeReport::next);
        playersViewMap.values().forEach(view -> view.cumulativeReport.next());
        threadsCumulativeReport.values().forEach(CumulativeReport::next);
    }

    /**
     * Main loop for updating player views. Ensures updates are performed within a
     * 50ms time frame.
     * 
     * @param canRun AtomicBoolean controlling whether the loop should continue
     *               running.
     */
    private void runView(AtomicBoolean canRun) {
        // Main loop
        while (canRun.get()) {
            // Start time
            long startTime = System.currentTimeMillis();

            try {
                // The view of each player
                playersViewMap.forEach((player, view) -> {
                    if (!view.install())
                        view.updateDistance();
                    view.moveTooFast = view.overSpeed();
                });
            } catch (Exception exception) {
                exception.printStackTrace();
            }

            // End time
            long endTime = System.currentTimeMillis();
            // Maximum time consumption 50 ms
            long needSleep = 50 - (endTime - startTime);
            if (needSleep > 0) {
                try {
                    Thread.sleep(needSleep);
                } catch (InterruptedException ignored) {
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
    private void runThread(AtomicBoolean canRun, CumulativeReport threadCumulativeReport) {
        while (canRun.get()) {
            long startTime = System.currentTimeMillis();
            long effectiveTime = startTime + 50;

            if (!globalPause) {
                try {
                    List<World> worldList = lastWorldList;
                    List<PlayerChunkView> viewList = Arrays
                            .asList(playersViewMap.values().toArray(new PlayerChunkView[0]));
                    Collections.shuffle(viewList);
                    for (PlayerChunkView view : viewList) {
                        view.move();
                    }
                    Map<World, List<PlayerChunkView>> worldsViews = new HashMap<>();
                    for (PlayerChunkView view : viewList) {
                        worldsViews.computeIfAbsent(view.getLastWorld(), key -> new ArrayList<>()).add(view);
                    }

                    handleServer: {
                        for (World world : worldList) {
                            ConfigData.World configWorld = configData.getWorld(world.getName());
                            if (!configWorld.enable)
                                continue;
                            CumulativeReport worldCumulativeReport = worldsCumulativeReport.get(world);
                            if (worldCumulativeReport == null)
                                continue;
                            NetworkTraffic worldNetworkTraffic = worldsNetworkTraffic.get(world);
                            if (worldNetworkTraffic == null)
                                continue;
                            if (serverNetworkTraffic.exceed(configData.getServerSendTickMaxBytes()))
                                break handleServer;
                            if (worldNetworkTraffic.exceed(configWorld.getWorldSendTickMaxBytes()))
                                continue;

                            AtomicInteger worldGeneratedChunk = worldsGeneratedChunk.getOrDefault(world,
                                    new AtomicInteger(Integer.MAX_VALUE));

                            handleWorld: {
                                boolean playersFull = false;
                                while (!playersFull && effectiveTime >= System.currentTimeMillis()) {
                                    playersFull = true;
                                    for (PlayerChunkView view : worldsViews.getOrDefault(world, new ArrayList<>(0))) {
                                        if (serverNetworkTraffic.exceed(configData.getServerSendTickMaxBytes()))
                                            break handleServer;
                                        if (worldNetworkTraffic.exceed(configWorld.getWorldSendTickMaxBytes()))
                                            break handleWorld;
                                        synchronized (view.networkTraffic) {
                                            Integer forciblySendSecondMaxBytes = view.forciblySendSecondMaxBytes;
                                            if (view.networkTraffic.exceed(forciblySendSecondMaxBytes != null
                                                    ? (int) (forciblySendSecondMaxBytes
                                                            * configData.playerNetworkSpeedUseDegree) / 20
                                                    : configWorld.getPlayerSendTickMaxBytes()))
                                                continue;
                                            if (configData.autoAdaptPlayerNetworkSpeed && view.networkTraffic
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
                                        long syncKey = view.syncKey;
                                        Long chunkKey = view.next();
                                        if (chunkKey == null) {
                                            view.waitSend = false;
                                            continue;
                                        }
                                        playersFull = false;
                                        int chunkX = ViewMap.getX(chunkKey);
                                        int chunkZ = ViewMap.getZ(chunkKey);

                                        handlePlayer: {
                                            if (!configData.disableFastProcess) {
                                                // Read the latest
                                                try {
                                                    if (configWorld.readServerLoadedChunk) {
                                                        BranchChunk chunk = branchMinecraft
                                                                .getChunkFromMemoryCache(world, chunkX, chunkZ);
                                                        if (chunk != null) {
                                                            // Read & write
                                                            serverCumulativeReport.increaseLoadFast();
                                                            worldCumulativeReport.increaseLoadFast();
                                                            view.cumulativeReport.increaseLoadFast();
                                                            threadCumulativeReport.increaseLoadFast();
                                                            List<Runnable> asyncRunnable = new ArrayList<>();
                                                            BranchChunkLight chunkLight = branchMinecraft
                                                                    .fromLight(world);
                                                            BranchNBT chunkNBT = chunk.toNBT(chunkLight, asyncRunnable);
                                                            asyncRunnable.forEach(Runnable::run);
                                                            sendChunk(world, configWorld, worldNetworkTraffic, view,
                                                                    chunkX, chunkZ, chunkNBT, chunkLight, syncKey,
                                                                    worldCumulativeReport, threadCumulativeReport);
                                                            break handlePlayer;
                                                        }
                                                    }
                                                } catch (NullPointerException | NoClassDefFoundError | NoSuchMethodError
                                                        | NoSuchFieldError exception) {
                                                    exception.printStackTrace();
                                                } catch (Exception ignored) {
                                                }

                                                try {
                                                    BranchNBT chunkNBT = branchMinecraft.getChunkNBTFromDisk(world,
                                                            chunkX, chunkZ);
                                                    if (chunkNBT != null && branchMinecraft.fromStatus(chunkNBT)
                                                            .isAbove(BranchChunk.Status.FULL)) {
                                                        serverCumulativeReport.increaseLoadFast();
                                                        worldCumulativeReport.increaseLoadFast();
                                                        view.cumulativeReport.increaseLoadFast();
                                                        threadCumulativeReport.increaseLoadFast();
                                                        sendChunk(world, configWorld, worldNetworkTraffic, view, chunkX,
                                                                chunkZ, chunkNBT,
                                                                branchMinecraft.fromLight(world, chunkNBT), syncKey,
                                                                worldCumulativeReport, threadCumulativeReport);
                                                        break handlePlayer;
                                                    }
                                                } catch (NullPointerException | NoClassDefFoundError | NoSuchMethodError
                                                        | NoSuchFieldError exception) {
                                                    exception.printStackTrace();
                                                } catch (Exception ignored) {
                                                }
                                            }

                                            boolean canGenerated = serverGeneratedChunk
                                                    .get() < configData.serverTickMaxGenerateAmount
                                                    && worldGeneratedChunk
                                                            .get() < configWorld.worldTickMaxGenerateAmount;
                                            if (canGenerated) {
                                                serverGeneratedChunk.incrementAndGet();
                                                worldGeneratedChunk.incrementAndGet();
                                            }

                                            try {
                                                // paper
                                                Chunk chunk = world.getChunkAtAsync(chunkX, chunkZ, canGenerated, true)
                                                        .get();
                                                if (chunk != null) {
                                                    serverCumulativeReport.increaseLoadSlow();
                                                    worldCumulativeReport.increaseLoadSlow();
                                                    view.cumulativeReport.increaseLoadSlow();
                                                    threadCumulativeReport.increaseLoadSlow();
                                                    try {
                                                        List<Runnable> asyncRunnable = new ArrayList<>();
                                                        BranchChunkLight chunkLight = branchMinecraft.fromLight(world);
                                                        BranchNBT chunkNBT = branchMinecraft.fromChunk(world, chunk)
                                                                .toNBT(chunkLight, asyncRunnable);
                                                        asyncRunnable.forEach(Runnable::run);
                                                        sendChunk(world, configWorld, worldNetworkTraffic, view, chunkX,
                                                                chunkZ, chunkNBT, chunkLight, syncKey,
                                                                worldCumulativeReport, threadCumulativeReport);
                                                        break handlePlayer;
                                                    } catch (NullPointerException | NoClassDefFoundError
                                                            | NoSuchMethodError | NoSuchFieldError exception) {
                                                        exception.printStackTrace();
                                                    } catch (Exception ignored) {
                                                    }
                                                } else if (configData.serverTickMaxGenerateAmount > 0
                                                        && configWorld.worldTickMaxGenerateAmount > 0) {
                                                    view.remove(chunkX, chunkZ);
                                                    break handlePlayer;
                                                }
                                            } catch (ExecutionException ignored) {
                                                view.remove(chunkX, chunkZ);
                                                break handlePlayer;
                                            } catch (NoSuchMethodError methodError) {
                                                // spigot (不推薦)
                                                if (canGenerated) {
                                                    serverCumulativeReport.increaseLoadSlow();
                                                    worldCumulativeReport.increaseLoadSlow();
                                                    view.cumulativeReport.increaseLoadSlow();
                                                    threadCumulativeReport.increaseLoadSlow();
                                                    try {
                                                        List<Runnable> asyncRunnable = new ArrayList<>();
                                                        BranchChunkLight chunkLight = branchMinecraft.fromLight(world);
                                                        CompletableFuture<BranchNBT> syncNBT = new CompletableFuture<>();
                                                        waitMoveSyncQueue
                                                                .add(() -> syncNBT
                                                                        .complete(branchMinecraft
                                                                                .fromChunk(world,
                                                                                        world.getChunkAt(chunkX,
                                                                                                chunkZ))
                                                                                .toNBT(chunkLight, asyncRunnable)));
                                                        BranchNBT chunkNBT = syncNBT.get();
                                                        asyncRunnable.forEach(Runnable::run);
                                                        sendChunk(world, configWorld, worldNetworkTraffic, view, chunkX,
                                                                chunkZ, chunkNBT, chunkLight, syncKey,
                                                                worldCumulativeReport, threadCumulativeReport);
                                                        break handlePlayer;
                                                    } catch (NullPointerException | NoClassDefFoundError
                                                            | NoSuchMethodError | NoSuchFieldError exception) {
                                                        exception.printStackTrace();
                                                    } catch (Exception ignored) {
                                                    }
                                                }
                                            } catch (InterruptedException ignored) {
                                            } catch (Exception ex) {
                                                ex.printStackTrace();
                                            }
                                        }

                                        view.waitSend = false;
                                    }

                                    try {
                                        Thread.sleep(0L);
                                    } catch (InterruptedException ignored) {
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception exception) {
                    exception.printStackTrace();
                }
            }

            long endTime = System.currentTimeMillis();
            long needSleep = 50 - (endTime - startTime);
            if (needSleep > 0) {
                try {
                    Thread.sleep(needSleep);
                } catch (InterruptedException ignored) {
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
    private void sendChunk(World world, ConfigData.World configWorld, NetworkTraffic worldNetworkTraffic,
            PlayerChunkView view, int chunkX, int chunkZ, BranchNBT chunkNBT, BranchChunkLight chunkLight, long syncKey,
            CumulativeReport worldCumulativeReport, CumulativeReport threadCumulativeReport) {
        BranchChunk chunk = branchMinecraft.fromChunk(world, chunkX, chunkZ, chunkNBT,
                configData.calculateMissingHeightMap);
        PlayerSendExtendChunkEvent event = new PlayerSendExtendChunkEvent(view.viewAPI, chunk, world);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled())
            return;

        if (configWorld.preventXray != null && !configWorld.preventXray.isEmpty()) {
            for (Map.Entry<BlockData, BlockData[]> conversionMap : configWorld.preventXray.entrySet())
                chunk.replaceAllMaterial(conversionMap.getValue(), conversionMap.getKey());
        }

        AtomicInteger consumeTraffic = new AtomicInteger(0);
        Consumer<Player> chunkAndLightPacket = branchPacket.sendChunkAndLight(view.getPlayer(), chunk, chunkLight,
                configWorld.sendTitleData, consumeTraffic::addAndGet);

        synchronized (view.networkSpeed) {
            Location nowLoc = view.getPlayer().getLocation();
            int nowChunkX = nowLoc.getBlockX() >> 4;
            int nowChunkZ = nowLoc.getBlockZ() >> 4;
            ViewMap viewMap = view.getMap();
            if (world != nowLoc.getWorld()) {
                view.getMap().markWaitPosition(chunkX, chunkZ);
                return;
            }
            if (view.getMap().isWaitPosition(chunkX, chunkZ))
                return;
            if (viewShape.isInsideEdge(nowChunkX, nowChunkZ, chunkX, chunkZ, viewMap.serverDistance))
                return;
            if (view.syncKey != syncKey)
                return;
            if (!running)
                return;

            boolean needMeasure = configData.autoAdaptPlayerNetworkSpeed && ((view.networkSpeed.speedID == null
                    && view.networkSpeed.speedTimestamp + 1000 <= System.currentTimeMillis())
                    || view.networkSpeed.speedTimestamp + 30000 <= System.currentTimeMillis());
            if (needMeasure) {
                if (view.networkSpeed.speedID != null) {
                    view.networkSpeed.add(30000, 0);
                }
                long pingID = random.nextLong();
                view.networkSpeed.pingID = pingID;
                view.networkSpeed.pingTimestamp = System.currentTimeMillis();
                branchPacket.sendKeepAlive(view.getPlayer(), pingID);
            }

            chunkAndLightPacket.accept(view.getPlayer());
            serverNetworkTraffic.use(consumeTraffic.get());
            worldNetworkTraffic.use(consumeTraffic.get());
            view.networkTraffic.use(consumeTraffic.get());
            serverCumulativeReport.addConsume(consumeTraffic.get());
            worldCumulativeReport.addConsume(consumeTraffic.get());
            view.cumulativeReport.addConsume(consumeTraffic.get());
            threadCumulativeReport.addConsume(consumeTraffic.get());

            if (needMeasure) {
                long speedID = random.nextLong();
                view.networkSpeed.speedID = speedID;
                view.networkSpeed.speedConsume = consumeTraffic.get();
                view.networkSpeed.speedTimestamp = System.currentTimeMillis();
                branchPacket.sendKeepAlive(view.getPlayer(), speedID);
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
    public void packetEvent(Player player, PacketEvent event) {
        PlayerChunkView view = getView(player);
        if (view == null)
            return;
        if (event instanceof PacketMapChunkEvent) {
            PacketMapChunkEvent chunkEvent = (PacketMapChunkEvent) event;
            view.send(chunkEvent.getChunkX(), chunkEvent.getChunkZ());
        }
    }

    /**
     * Respawns a player's view and sends a view distance packet after a delay.
     * 
     * @param player The player whose view is being respawned.
     */
    public void respawnView(Player player) {
        PlayerChunkView view = getView(player);
        if (view == null)
            return;
        view.delay();
        waitMoveSyncQueue.add(() -> branchPacket.sendViewDistance(player, view.getMap().extendDistance));
    }

    /**
     * Unloads a player's view if they move beyond a certain distance or to a
     * different world.
     * 
     * @param player The player whose view is being unloaded.
     * @param from   The player's previous location.
     * @param move   The player's new location.
     */
    public void unloadView(Player player, Location from, Location move) {
        PlayerChunkView view = getView(player);
        if (view == null)
            return;
        int blockDistance = view.getMap().extendDistance << 4;
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
        running = false;
        for (BukkitTask task : bukkitTasks)
            task.cancel();
        multithreadedService.shutdown();
    }
}